package spim.process.interestpointregistration.pairwise.range;

public class AllInRange< V > implements RangeComparator< V >
{
	@Override
	public boolean inRange( final V view1, final V view2 ) { return true; }
}
