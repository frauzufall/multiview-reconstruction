package spim.headless.psf;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.headless.registration.TestRegistration;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.psf.PSFCombination;
import spim.process.psf.PSFExtraction;

public class TestPSF
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testPSF( spimData, true );
	}

	public static void testPSF( final SpimData2 spimData, final boolean display )
	{
		new ImageJ();

		// run registration
		TestRegistration.testRegistration( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		final String label = "beads"; // this could be different for each ViewId

		final ArrayList< Pair< PSFExtraction< FloatType >, AffineTransform3D > > psfs = new ArrayList<>();

		for ( final ViewId viewId : viewIds )
		{
			final PSFExtraction< FloatType > psf = new PSFExtraction< FloatType >( new FloatType(), new long[]{ 15, 15, 19 } );
			psf.extractNext( spimData, viewId, label, true );

			spimData.getViewRegistrations().getViewRegistration( viewId ).updateModel();

			psfs.add( new ValuePair<>( psf, spimData.getViewRegistrations().getViewRegistration( viewId ).getModel() ) );

			//ImageJFunctions.show( psf.getPSF() );
			//ImageJFunctions.show( psf.getTransformedNormalizedPSF( spimData.getViewRegistrations().getViewRegistration( viewId ).getModel() ) );
		}

		final PSFCombination< FloatType > psf = new PSFCombination<>( psfs );

		if ( display )
		{
			ImageJFunctions.show( psf.computeAverageTransformedPSF() );
			ImageJFunctions.show( psf.computeMaxAverageTransformedPSF() );
		}
	}
}