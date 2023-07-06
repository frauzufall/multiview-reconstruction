package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;

public class InterestPointsN5 extends InterestPoints
{
	public static int defaultBlockSize = 300_000;
	public static final String baseN5 = "interestpoints.n5";

	final String n5path;
	ArrayList< InterestPoint > interestPoints;
	ArrayList< CorrespondingInterestPoints > correspondingInterestPoints;

	protected InterestPointsN5( final File baseDir, final String n5path )
	{
		super(baseDir);
		this.n5path = n5path;
	}

	public String getN5path() { return n5path; }

	@Override
	public String getXMLRepresentation() {
		// a hack so that windows does not put its backslashes in
		return getN5path().toString().replace( "\\", "/" );
	}

	@Override
	public String createXMLRepresentation( final ViewId viewId, final String label )
	{
		return new File( "tpId_" + viewId.getTimePointId() + "_viewSetupId_" + viewId.getViewSetupId() + "/" + label ).getPath();
	}

	/**
	 * @return - a list of interest points (copied), tries to load from disc if null
	 */
	@Override
	public synchronized List< InterestPoint > getInterestPointsCopy()
	{
		if ( this.interestPoints == null )
			loadInterestPoints();

		final ArrayList< InterestPoint > list = new ArrayList< InterestPoint >();

		for ( final InterestPoint p : this.interestPoints )
			list.add( new InterestPoint( p.id, p.getL().clone() ) );

		return list;
	}

	/**
	 * @return - the list of corresponding interest points (copied), tries to load from disc if null
	 */
	public synchronized List< CorrespondingInterestPoints > getCorrespondingInterestPointsCopy()
	{
		if ( this.correspondingInterestPoints == null )
			loadCorrespondences();

		final ArrayList< CorrespondingInterestPoints > list = new ArrayList< CorrespondingInterestPoints >();

		for ( final CorrespondingInterestPoints p : this.correspondingInterestPoints )
			list.add( new CorrespondingInterestPoints( p ) );

		return list;
	}

	@Override
	protected void setInterestPointsLocal( final List< InterestPoint > list )
	{
		if ( list.getClass().isInstance( ArrayList.class ))
			this.interestPoints = (ArrayList<InterestPoint>)list;
		else
			this.interestPoints = new ArrayList<>( list );
	}

	@Override
	protected void setCorrespondingInterestPointsLocal( final List< CorrespondingInterestPoints > list )
	{
		if ( list.getClass().isInstance( ArrayList.class ))
			this.correspondingInterestPoints = (ArrayList<CorrespondingInterestPoints>)list;
		else
			this.correspondingInterestPoints = new ArrayList<>( list );
	}

	public String ipDataset() { return new File( getN5path(), "interestpoints" ).getPath(); }
	public String corrDataset() { return new File( getN5path(), "correspondences" ).getPath(); }

	@Override
	public boolean saveInterestPoints( final boolean forceWrite )
	{
		if ( !modifiedInterestPoints && !forceWrite )
			return true;

		final ArrayList< InterestPoint > list = this.interestPoints;

		if ( list == null )
			return false;

		final String dataset = ipDataset();

		try
		{
			final N5FSWriter n5Writer = new N5FSWriter( new File( baseDir.getAbsolutePath(), baseN5 ).getAbsolutePath() );

			if (n5Writer.exists(dataset))
				n5Writer.remove(dataset);

			n5Writer.createGroup(dataset);

			n5Writer.setAttribute(dataset, "pointcloud", "1.0.0");
			n5Writer.setAttribute(dataset, "type", "list");

			final String idDataset = dataset + "/id";
			final String locDataset = dataset + "/loc";

			if ( list.size() == 0 )
			{
				n5Writer.createDataset(
						idDataset,
						new long[] {0},
						new int[] {1},
						DataType.UINT64,
						new GzipCompression());

				n5Writer.createDataset(
						locDataset,
						new long[] {0},
						new int[] {1},
						DataType.FLOAT64,
						new GzipCompression());

				return true;
			}

			final int n = list.get( 0 ).getL().length;

			// 1 x N array (which is a 2D array)
			final FunctionRandomAccessible< UnsignedLongType > id =
					new FunctionRandomAccessible<>(
							2,
							(location, value) -> value.set( list.get( location.getIntPosition( 1 ) ).id ),
							UnsignedLongType::new );

			// DIM x N array (which is a 2D array)
			final FunctionRandomAccessible< DoubleType > loc =
					new FunctionRandomAccessible<>(
							2,
							(location, value) ->
							{
								final InterestPoint ip = list.get( location.getIntPosition( 1 ) );
								value.set( ip.getL()[ location.getIntPosition( 0 ) ] );
							},
							DoubleType::new );

			final RandomAccessibleInterval< UnsignedLongType > idData =
					Views.interval( id, new long[] { 0, 0 }, new long[] { 0, list.size() - 1 } );

			final RandomAccessibleInterval< DoubleType > locData =
					Views.interval( loc, new long[] { 0, 0 }, new long[] { n - 1, list.size() - 1 } );

			N5Utils.save( idData, n5Writer, idDataset, new int[] { 1, defaultBlockSize }, new GzipCompression() );
			N5Utils.save( locData, n5Writer, locDataset, new int[] { (int)locData.dimension( 0 ), defaultBlockSize }, new GzipCompression() );

			n5Writer.close();
		}
		catch (IOException e)
		{
			IOFunctions.println("Couldn't write interestpoints to N5 '" + baseDir + ":/" + dataset + "': " + e );
			e.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	public boolean saveCorrespondingInterestPoints(boolean forceWrite)
	{
		if ( !modifiedCorrespondingInterestPoints && !forceWrite )
			return true;

		final ArrayList< CorrespondingInterestPoints > list = this.correspondingInterestPoints;

		if ( list == null )
			return false;

		final String dataset = corrDataset();

		try
		{
			final N5FSWriter n5Writer = new N5FSWriter( new File( baseDir.getAbsolutePath(), baseN5 ).getAbsolutePath() );

			if (n5Writer.exists(dataset))
				n5Writer.remove(dataset);

			n5Writer.createGroup(dataset);

			n5Writer.setAttribute( dataset, "correspondences", "1.0.0");

			final String corrDataset = dataset + "/data";

			if ( list.size() == 0 )
			{
				n5Writer.setAttribute( dataset, "idMap", new HashMap< String, Long >() );
				return true;
			}

			//
			// assemble all ViewIds+Labels that there are correspondences with
			// each combination of (ViewId, label) is assigned an ID, this mapping is stored in the attributes
			// the dataset itself only stores the ID as UINT64
			//
			final HashMap<ViewId, HashSet<String>> viewidToLabels = new HashMap<>();

			for ( final CorrespondingInterestPoints cip : list )
			{
				final ViewId viewId = cip.getCorrespondingViewId();
				final String label = cip.getCorrespodingLabel();

				if ( viewidToLabels.containsKey( viewId ) )
				{
					viewidToLabels.get( viewId ).add( label );
				}
				else
				{
					final HashSet< String > labels = new HashSet<>();
					labels.add( label );
					viewidToLabels.put( viewId, labels );
				}
			}

			final HashMap< String, Long > idMap = new HashMap<>(); // to store ID
			final HashMap< ViewId, HashMap<String, Long>> quickLookup = new HashMap<>(); // to quickly lookup ID while saving
			long id = 0;

			for ( final ViewId viewId : viewidToLabels.keySet() )
			{
				final HashMap<String, Long > map = new HashMap<>();
				quickLookup.put( viewId, map );

				for ( final String label : viewidToLabels.get( viewId ) )
				{
					idMap.put( viewId.getTimePointId() + "," + viewId.getViewSetupId() + "," + label, id );
					map.put( label, id );
					id++;
				}
			}

			n5Writer.setAttribute( dataset, "idMap", idMap );

			// 3 x N array (which is a 2D array, ID_a, ID_b, ID)
			final FunctionRandomAccessible< UnsignedLongType > corrId =
					new FunctionRandomAccessible<>(
							2,
							(location, value) -> {
								final CorrespondingInterestPoints cip = list.get( location.getIntPosition( 1 ) );
								final int x = location.getIntPosition( 0 );
								if ( x == 0 )
									value.set( cip.getDetectionId() );
								else if ( x == 1 )
									value.set( cip.getCorrespondingDetectionId() );
								else
									value.set( quickLookup.get( cip.getCorrespondingViewId() ).get( cip.getCorrespodingLabel() ) );
							},
							UnsignedLongType::new );

			final RandomAccessibleInterval< UnsignedLongType > corrIdData =
					Views.interval( corrId, new long[] { 0, 0 }, new long[] { 2, list.size() - 1 } );

			N5Utils.save( corrIdData, n5Writer, corrDataset, new int[] { 1, defaultBlockSize }, new GzipCompression() );

			/*
			n5Writer.createDataset(
					dataset,
					new long[] {1},
					new int[] {1},
					DataType.OBJECT,
					new GzipCompression());

			final DatasetAttributes datasetAttributes = n5Writer.getDatasetAttributes(dataset);

			n5Writer.writeSerializedBlock(
					list,
					dataset,
					datasetAttributes,
					new long[] {0});
			*/
			n5Writer.close();
		}
		catch (IOException e)
		{
			IOFunctions.println("Couldn't write corresponding interestpoints to N5 '" + baseDir + ":/" + dataset + "': " + e );
			e.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	protected boolean loadInterestPoints()
	{
		try
		{
			final N5FSReader n5 = new N5FSReader( new File( baseDir.getAbsolutePath(), baseN5 ).getAbsolutePath() );
			final String dataset = ipDataset();

			if (!n5.exists(dataset))
			{
				IOFunctions.println( "InterestPointsN5.loadInterestPoints(): dataset '" + baseDir + ":/" + dataset + "' does not exist, cannot load interestpoints." );
				return false;
			}

			String version = n5.getAttribute(dataset, "pointcloud", String.class );
			String type = n5.getAttribute(dataset, "type", String.class );

			System.out.println( version + ", " + type );

			final String idDataset = dataset + "/id";
			final String locDataset = dataset + "/loc";

			// 1 x N array (which is a 2D array)
			final RandomAccessibleInterval< UnsignedLongType > idData = N5Utils.open( n5, idDataset );

			// DIM x N array (which is a 2D array)
			final RandomAccessibleInterval< DoubleType > locData = N5Utils.open( n5, locDataset );
			final int n = (int)locData.dimension( 0 );

			final RandomAccess< UnsignedLongType > idRA = idData.randomAccess();
			final RandomAccess< DoubleType > locRA = locData.randomAccess();

			final ArrayList< InterestPoint > list = new ArrayList<>();

			idRA.setPosition( 0, 0 );
			idRA.setPosition( 0, 1 );
			locRA.setPosition( 0, 0 );
			locRA.setPosition( 0, 1 );

			for ( int i = 0; i < idData.dimension( 1 ); ++ i )
			{
				final long id = idRA.get().get();
				final double[] loc = new double[ n ];

				for ( int d = 0; d < n; ++d )
				{
					loc[ d ] = locRA.get().get();

					if ( d != n - 1 )
						locRA.fwd( 0 );
				}

				for ( int d = 0; d < n - 1; ++d )
					locRA.bck( 0 );

				if ( i != idData.dimension( 1 ) - 1 )
				{
					idRA.fwd( 1 );
					locRA.fwd( 1 );
				}

				list.add( new InterestPoint( (int)id, loc) );
			}

			/*
			final DatasetAttributes datasetAttributes = n5.getDatasetAttributes(dataset);

			this.interestPoints = n5.readSerializedBlock( dataset, datasetAttributes, 0 );*/

			this.interestPoints = list;
			modifiedInterestPoints = false;

			n5.close();

			return true;
		} 
		catch ( final Exception e )
		{
			this.interestPoints = new ArrayList<>();
			IOFunctions.println( "InterestPointsN5.loadInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}
	}

	@Override
	protected boolean loadCorrespondences()
	{
		// TODO: loading not implemented yet!!
		try
		{

			final N5FSReader n5 = new N5FSReader( new File( baseDir.getAbsolutePath(), baseN5 ).getAbsolutePath() );
			final String dataset = corrDataset();

			if (!n5.exists(dataset))
			{
				IOFunctions.println( "InterestPointsN5.loadCorrespondences(): dataset '" + baseDir + ":/" + baseN5 + "/" + dataset + "' does not exist, cannot load interestpoints." );
				return false;
			}

			String version = n5.getAttribute(dataset, "correspondences", String.class );
			HashMap< String, Long > idMap = n5.getAttribute(dataset, "type", HashMap.class ); // to store ID

			System.out.println( version + ", " + idMap.size() + " correspondence codes" );

			if ( idMap.size() == 0 )
			{
				this.correspondingInterestPoints = new ArrayList<>();
				modifiedCorrespondingInterestPoints = false;

				return true;
			}

			final String corrDataset = dataset + "/data";

			// 3 x N array (which is a 2D array, ID_a, ID_b, ID)
			final RandomAccessibleInterval< UnsignedLongType > idData = N5Utils.open( n5, corrDataset );

			final RandomAccess< UnsignedLongType > corrRA = idData.randomAccess();

			final ArrayList< CorrespondingInterestPoints > correspondingInterestPoints = new ArrayList<>();

			corrRA.setPosition( 0, 0 );
			corrRA.setPosition( 0, 1 );

			/*
			final N5FSReader n5 = new N5FSReader( new File( baseDir.getAbsolutePath(), baseN5 ).getAbsolutePath() );
			final String dataset = corrDataset();

			if (!n5.exists(dataset))
			{
				IOFunctions.println( "InterestPointsN5.loadCorrespondingInterestPoints(): dataset '" + baseDir + ":/" + dataset + "' does not exist, cannot load interestpoints." );
				return false;
			}

			final DatasetAttributes datasetAttributes = n5.getDatasetAttributes(dataset);

			this.correspondingInterestPoints = n5.readSerializedBlock( dataset, datasetAttributes, 0 );
			modifiedCorrespondingInterestPoints = false;

			n5.close();
			*/

			return true;
		} 
		catch ( final Exception e )
		{
			this.interestPoints = new ArrayList<>();
			IOFunctions.println( "InterestPointsN5.loadCorrespondingInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean deleteInterestPoints()
	{
		try
		{
			final N5FSWriter n5Writer = new N5FSWriter( baseDir.getAbsolutePath() );
	
			if (n5Writer.exists(ipDataset()))
				n5Writer.remove(ipDataset());
	
			n5Writer.close();

			return true;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "InterestPointsN5.deleteInterestPoints(): " + e );
			e.printStackTrace();

			return false;
		}
	}

	@Override
	public boolean deleteCorrespondingInterestPoints()
	{
		try
		{
			final N5FSWriter n5Writer = new N5FSWriter( baseDir.getAbsolutePath() );
	
			if (n5Writer.exists(corrDataset()))
				n5Writer.remove(corrDataset());
	
			n5Writer.close();

			return true;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "InterestPointsN5.deleteCorrespondingInterestPoints(): " + e );
			e.printStackTrace();

			return false;
		}
	}

}