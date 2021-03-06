package params;

public class Params {

	/** Constellation considered */
	// public final static String constellation = "02sat";
	// public final static String constellation = "08sat";
	public final static String constellation = "18sat";
	/** Planning horizon considered */
	public final static String horizon = "24h"; 
	//public final static String horizon = "12h";
	//public final static String horizon = "24h";
	/** File containing a description of all static data (satellites, users, stations) */
	public final static String systemDataFile = "data/system_data_"+constellation+".xml";
	/** File containing a description of all dynamic data (candidate acquisitions, recorded acquisitions...) */
	public final static String planningDataFile = "data/planning_data_"+constellation+"_"+horizon+".xml";	
	/** Approximation of the rotation speed of the satellite (in radians per second) */
	public final static double meanRotationSpeed = (2*Math.PI)/180; // 2 degrees per second
	/** Rate associated with data downlink to ground stations (in bits per second) */
	public final static double downlinkRate = 1E6;


	
	public final static double priorityCloudProbaWeight = 2.;

	/** Borders of the cloud probability subgroups :
	 *  the subgroups are [                   0.0;  probabilityBorders(0)]
	 *                    [ probabilityBorders(0);  probabilityBorders(1)]
	 *                    ...
	 *                    [probabilityBorders(-2); probabilityBorders(-1)]
	 * Used in a version of the acquisition planner.
	 * This will neglect all windows with cloudProba > probabilityBorders(-1). */
	public final static Double[] probabilityBorders = {0.1, 0.2, 0.4, 0.8};

	public final static double waitingTime = 1.;

	// For the random process, number of times the process is run before the best result is returned
	public final static int n_runs = 5;
}
