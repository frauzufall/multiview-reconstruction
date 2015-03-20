package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.TextField;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Util;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.XmlIoSpimData2;

public class Specify_Calibration implements PlugIn
{
	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "specifying calibration", true, true, true, true ) )
			return;

		final SpimData2 data = result.getData();
		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final ArrayList< Cal > calibrations = findCalibrations( data, viewIds );

		final Cal maxCal = mostPresentCal( calibrations );

		if ( !queryNewCal( calibrations, maxCal ) )
			return;

		applyCal( maxCal, data, viewIds );

		// save the xml
		final XmlIoSpimData2 io = new XmlIoSpimData2( result.getClusterExtension() );
		
		final String xml = new File( result.getData().getBasePath(), new File( result.getXMLFileName() ).getName() ).getAbsolutePath();
		try 
		{
			io.save( result.getData(), xml );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + io.lastFileName() + "'." );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + io.lastFileName() + "': " + e );
			e.printStackTrace();
		}
	}

	public static boolean queryNewCal( final ArrayList< Cal > calibrations, final Cal maxCal )
	{
		final GenericDialog gd = new GenericDialog( "Define new calibration" );
		
		gd.addNumericField( "Calibration_x", maxCal.getCal()[ 0 ], 40, 20, "" );
		// ImageJ cuts of part of the number otherwise
		((TextField)gd.getNumericFields().lastElement()).setText( "" + maxCal.getCal()[ 0 ] );
		gd.addNumericField( "Calibration_y", maxCal.getCal()[ 1 ], 40, 20, "" );
		// ImageJ cuts of part of the number otherwise
		((TextField)gd.getNumericFields().lastElement()).setText( "" + maxCal.getCal()[ 1 ] );
		gd.addNumericField( "Calibration_z", maxCal.getCal()[ 2 ], 40, 20, "" );
		// ImageJ cuts of part of the number otherwise
		((TextField)gd.getNumericFields().lastElement()).setText( "" + maxCal.getCal()[ 2 ] );
		gd.addStringField( "Unit", maxCal.unit() );

		if ( calibrations.size() > 1 )
			gd.addMessage( "WARNING: Calibrations are not the same for all\n" +
						   "view setups! All calibrations will be overwritten\n" +
						   "for all view setups if defined here.",
						   GUIHelper.mediumstatusfont, GUIHelper.warning );

		gd.addMessage( "Note: These values will be applied to selected view\n" +
					   "setups, existing registration are not affected and\n" +
					   "will need to be recomputed if necessary.",
					   GUIHelper.mediumstatusfont );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		maxCal.getCal()[ 0 ] = gd.getNextNumber();
		maxCal.getCal()[ 1 ] = gd.getNextNumber();
		maxCal.getCal()[ 2 ] = gd.getNextNumber();
		maxCal.setUnit( gd.getNextString() );

		return true;
	}

	public static void applyCal( final Cal maxCal, final AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > > spimData, final List< ViewId > viewIds )
	{
		// this is the same for all timepoints, we are just interested in the ViewSetup
		final TimePoint t = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 );

		for ( final ViewId viewId : viewIds )
		{
			if ( viewId.getTimePointId() != t.getId() )
				continue;

			final BasicViewDescription< ? > desc = spimData.getSequenceDescription().getViewDescriptions().get( viewId );
			final BasicViewSetup viewSetup = desc.getViewSetup();

			// TODO: this should not be necessary
			((ViewSetup)viewSetup).setVoxelSize( new FinalVoxelDimensions( maxCal.unit(),
					maxCal.getCal()[ 0 ],
					maxCal.getCal()[ 1 ],
					maxCal.getCal()[ 2 ] ) );
		}
	}

	public static ArrayList< Cal > findCalibrations( final AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > > spimData, final List< ViewId > viewIds )
	{
		// this is the same for all timepoints, we are just interested in the ViewSetup
		final TimePoint t = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 );

		final ArrayList< Cal > calibrations = new ArrayList< Cal >(); 
		
		for ( final ViewId viewId : viewIds )
		{
			if ( viewId.getTimePointId() != t.getId() )
				continue;

			final BasicViewDescription< ? > vd = spimData.getSequenceDescription().getViewDescriptions().get( viewId );
			final BasicViewSetup vs = vd.getViewSetup();
			final String name;

			if ( ViewSetup.class.isInstance( vs ) )
			{
				name =
					"angle: " + ((ViewSetup)vs).getAngle().getName() +
					" channel: " + ((ViewSetup)vs).getChannel().getName() +
					" illum: " + ((ViewSetup)vs).getIllumination().getName() +
					", present at timepoint: " + t.getName() +
					": " + vd.isPresent();
			}
			else
			{
				name =
					"viewsetup: " + vs.getId() + ", present at timepoint: " +
					t.getName() + ": " + vd.isPresent();
			}

			// only consider voxelsizes as defined in the XML
			VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSize( vs );

			if ( voxelSize == null )
				voxelSize = new FinalVoxelDimensions( "", new double[]{ 1, 1, 1 } );

			final double x = voxelSize.dimension( 0 );
			final double y = voxelSize.dimension( 1 );
			final double z = voxelSize.dimension( 2 );
			String unit = voxelSize.unit();

			if ( unit == null )
				unit = "";

			IOFunctions.println( "cal: [" + x + ", " + y + ", " + z + "] " + unit + "  -- " + name );

			final Cal calTmp = new Cal( new double[]{ x, y, z }, unit );
			boolean foundMatch = false;

			for ( int j = 0; j < calibrations.size() && !foundMatch; ++j )
			{
				final Cal cal = calibrations.get( j );
				if ( cal.equals( calTmp ) )
				{
					cal.increaseCount();
					foundMatch = true;
				}
			}

			if ( !foundMatch )
				calibrations.add( calTmp );
		}

		return calibrations;
	}

	public static Cal mostPresentCal( final Collection< Cal > calibrations )
	{
		int max = 0;
		Cal maxCal = null;
		
		for ( final Cal cal : calibrations )
		{
			if ( cal.getCount() > max )
			{
				max = cal.getCount();
				maxCal = cal;
			}
		}
		
		IOFunctions.println( "Number of calibrations: " + calibrations.size() );
		IOFunctions.println( "Calibration most often present: " + Util.printCoordinates( maxCal.getCal() ) + " (" + maxCal.getCount() + " times)" );

		return maxCal;
	}

	public static class Cal
	{
		final double[] cal;
		int count;
		String unit;

		public Cal( final double[] cal, final String unit )
		{
			this.cal = cal;
			this.count = 1;
			this.unit = unit;
		}
		
		public void increaseCount() { ++count; }
		public int getCount() { return count; }
		public double[] getCal() { return cal; }
		public String unit() { return unit; }
		public void setUnit( final String unit ) { this.unit = unit; }

		@Override
		public boolean equals( final Object o )
		{
			if ( o instanceof Cal )
			{
				final Cal c2 = (Cal)o;
				
				if ( c2.cal.length != this.cal.length )
					return false;
				else
				{
					for ( int d = 0; d < cal.length; ++d )
						if ( c2.cal[ d ] != cal[ d ] )
							return false;
					
					return true;
				}
			}
			else
				return false;
		}
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();
		new Specify_Calibration().run( null );
	}
}
