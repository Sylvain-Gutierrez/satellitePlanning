package solver;

import problem.Satellite;
import problem.Station;
import problem.Acquisition;

/**
 * @author sylvain
 *
 */
public class DownloadPlanLine {

	/** Satellite associated with this download window */
	public final Satellite satellite;
	/** Station associated with this download window */
	public final Station station;
	/** Start time of this download window */
	public final double start;
	/** End time of this download window */
	public final double end;
	public final Acquisition acquisition;

	/** Index of this download window in the list of download windows of the problem */
	// public final int idx;
	
	/**
	 * Create a download plan line
	 * @param id 
	 * @param satellite
	 * @param station
	 * @param start
	 * @param end
     * @param acquisition
	 */
	public DownloadPlanLine(Satellite satellite, Station station, double start, double end, Acquisition acq){ //, int idxInDownloadWindows){
		this.satellite = satellite;
		this.station = station;
		this.start = start;
		this.end = end;
        this.acquisition = acq;
		// this.idx = idxInDownloadWindows; 
	}
}