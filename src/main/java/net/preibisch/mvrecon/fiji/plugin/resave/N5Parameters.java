package net.preibisch.mvrecon.fiji.plugin.resave;

import java.awt.Font;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.Bzip2Compression;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.Lz4Compression;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.XzCompression;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.sequence.ViewSetup;
import net.preibisch.legacy.io.IOFunctions;

public class N5Parameters
{
	public static String[] compressions = new String[]{ "Bzip2", "Gzip", "Lz4", "Raw (no compression)", "Xz" };
	public static int defaultBlockSize = 64;
	public static int defaultCompression = 1;
	public static int defaultNumThreads = Runtime.getRuntime().availableProcessors();


	public File xmlFile, n5File;

	public int[][] resolutions, subdivisions;
	public Map< Integer, ExportMipmapInfo > proposedMipmaps;

	public Compression compression;
	public int numCellCreatorThreads = 1;

	public boolean saveXML = true; // mostly important for cluster-based re-saving
	public boolean saveData = true; // mostly important for cluster-based re-saving

	public boolean setFinishedAttributeInN5 = true; // required if double-checking that all ViewId were written
	final public static String finishedAttrib = "saved_completely"; // required if double-checking that all ViewId were written

	public static N5Parameters getParamtersIJ(
			final String xmlFileName,
			final List< ViewSetup > setupsToProcess )
	{
		final N5Parameters n5params = new N5Parameters();

		n5params.xmlFile = new File( xmlFileName.replace( ".xml", "-n5.xml" ) ); //new File(xml.getXMLFileName().replace( ".xml", "-n5.xml" ));
		n5params.n5File = new File( xmlFileName.replace( ".xml", ".n5" ));

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( setupsToProcess ); //xml.getViewSetupsToProcess() );
		final int firstviewSetupId = setupsToProcess.get( 0 ).getId();// xml.getData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		final ExportMipmapInfo autoMipmapSettings = perSetupExportMipmapInfo.get( firstviewSetupId );

		// block size should be bigger than hdf5
		for ( final int[] row : autoMipmapSettings.getSubdivisions() )
			Arrays.fill( row, defaultBlockSize );

		final GenericDialogPlus gdp = new GenericDialogPlus( "Options" );

		gdp.addMessage( "N5 saving options", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gdp.addChoice( "Compression", compressions, compressions[ defaultCompression ] );

		gdp.addStringField( "Subsampling_factors", ProposeMipmaps.getArrayString( autoMipmapSettings.getExportResolutions() ), 40 );
		gdp.addStringField( "N5_block_sizes", ProposeMipmaps.getArrayString( autoMipmapSettings.getSubdivisions() ), 40 );

		gdp.addMessage( "Cluster-related options", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gdp.addNumericField( "Number_of_threads (CPUs:" + Runtime.getRuntime().availableProcessors() + ")", defaultNumThreads, 0 );
		gdp.addFileField( "Output_XML", n5params.xmlFile.getAbsolutePath(), 75 );
		gdp.addFileField( "Output_N5", n5params.n5File.getAbsolutePath(), 75 );
		gdp.addCheckbox( "Write_XML", true );
		gdp.addCheckbox( "Write_data", true );

		gdp.showDialog();

		if (gdp.wasCanceled())
			return null;

		final int compression = defaultCompression = gdp.getNextChoiceIndex();

		String subsampling = gdp.getNextString();
		String chunkSizes = gdp.getNextString();

		n5params.numCellCreatorThreads = defaultNumThreads = Math.max( 1, (int)Math.round( gdp.getNextNumber() ) );

		n5params.xmlFile = new File(gdp.getNextString());
		n5params.n5File = new File(gdp.getNextString());

		n5params.saveXML = gdp.getNextBoolean();
		n5params.saveData = gdp.getNextBoolean();

		if ( compression == 0 ) // "Bzip2", "Gzip", "Lz4", "Raw (no compression)", "Xz"
			n5params.compression = new Bzip2Compression();
		else if ( compression == 1 )
			n5params.compression = new GzipCompression();
		else if ( compression == 2 )
			n5params.compression = new Lz4Compression();
		else if ( compression == 4 )
			n5params.compression = new XzCompression();
		else
			n5params.compression = new RawCompression();

		final int[][] resolutions = PluginHelper.parseResolutionsString( subsampling );
		final int[][] subdivisions = PluginHelper.parseResolutionsString( chunkSizes );

		if ( resolutions.length == 0 )
		{
			IOFunctions.println( "Cannot parse subsampling factors " + subsampling );
			return null;
		}
		if ( subdivisions.length == 0 )
		{
			IOFunctions.println( "Cannot parse hdf5 chunk sizes " + chunkSizes );
			return null;
		}
		else if ( resolutions.length != subdivisions.length )
		{
			IOFunctions.println( "subsampling factors and hdf5 chunk sizes must have the same number of elements" );
			return null;
		}

		n5params.proposedMipmaps = createProposedMipMaps(
				resolutions,
				subdivisions,
				setupsToProcess.stream().map( vs -> vs.getId() ).collect( Collectors.toList() ) );

		return n5params;
	}

	public static Map< Integer, ExportMipmapInfo > createProposedMipMaps(
			final int[][] resolutions,
			final int[][] subdivisions,
			final Collection< Integer > setupIds )
	{
		final Map< Integer, ExportMipmapInfo > proposedMipmaps = new HashMap<>();

		final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( resolutions, subdivisions );
		for ( final int setupId : setupIds )
			proposedMipmaps.put( setupId, mipmapInfo );

		return proposedMipmaps;
	}
}