package solver;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import params.Params;
import problem.Acquisition;
import problem.AcquisitionWindow;
import problem.CandidateAcquisition;
import problem.PlanningProblem;
import problem.ProblemParserXML;
import problem.Satellite;


/**
 * Acquisition planner which solves the acquisition problem based on a greedy algorithm
 * which tries to plan at each step one additional acquisition, while there are candidate acquisitions left.
 * Criteria involved in this planning are the priority and cloud probability.
 * This version adds a random process and the ability to run the code several times and chose the best planning
 * based on our criteria and the number of acquisition that were planned.
 * @author GutGros
 *
 */
public class AcquisitionPlannerGreedySimpleAcquisitionsRandomProcess {

	/** Planning problem for which this acquisition planner is used */
	private final PlanningProblem planningProblem;
	/** Data structure used for storing the plan of each satellite */
	private final Map<Satellite,SatellitePlan> satellitePlans;

	
	/**
	 * Build an acquisition planner for a planning problem
	 * @param planningProblem
	 */
	public AcquisitionPlannerGreedySimpleAcquisitionsRandomProcess(PlanningProblem planningProblem){
		this.planningProblem = planningProblem;
		satellitePlans = new HashMap<Satellite,SatellitePlan>();
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
	}

	/**
	 * Planning function which uses a greedy algorithm.
	 * Candidate acquisitions are transformed into simple acquisitions, i.e. associated with
	 * a single acquisition window.
	 * This allows to sort these acquisitions based on their priority AND cloud probability.
	 * Subgroups of acquisitions with relatively close importance (=linear combination of
	 * priority and cloud probability) are made and then shuffled. This allows to keep the
	 * best candidates first and introduce a random process to find a solution that maximizes
	 * the number of acquisitions planned by running it several times.
	 * 
	 */
	public void planAcquisitions(){
		
		List<CandidateAcquisition> candidateAcquisitions = new ArrayList<CandidateAcquisition>(planningProblem.candidateAcquisitions);
        int nCandidates = candidateAcquisitions.size();
		int nPlanned = 0;
        
		// Make simple acquisitions (with single associated window) and separate them in lists associated with priority
		List<CandidateAcquisition> simpleCandidateAcquisitionsPriority0 = new ArrayList<CandidateAcquisition>();
		List<CandidateAcquisition> simpleCandidateAcquisitionsPriority1 = new ArrayList<CandidateAcquisition>();
		for(CandidateAcquisition acq : candidateAcquisitions){
				for(AcquisitionWindow w : acq.acquisitionWindows){
					CandidateAcquisition temp_acq = new CandidateAcquisition(acq.name, acq.user, acq.priority, acq.longitude, acq.latitude, acq.idx);
					temp_acq.acquisitionWindows.add(w);
					if (acq.priority == 0){
						simpleCandidateAcquisitionsPriority0.add(temp_acq);
					} else {
						simpleCandidateAcquisitionsPriority1.add(temp_acq);
					}
				}
			}


		// Sort the lists (prio0 and prio1) wrt cloud probability
		Comparator<CandidateAcquisition> CloudProbaComparator = new Comparator<CandidateAcquisition>(){
			public int compare(CandidateAcquisition a1,CandidateAcquisition a2){
				return (int) (a1.acquisitionWindows.get(0).cloudProba - a2.acquisitionWindows.get(0).cloudProba);
			}};
		Collections.sort(simpleCandidateAcquisitionsPriority0,CloudProbaComparator);
		Collections.sort(simpleCandidateAcquisitionsPriority1,CloudProbaComparator);

		// Separate the lists in subgroups of close importances.

			//prio0
		List<Double> borders = Arrays.asList(Params.probabilityBorders);
		List<CandidateAcquisition> current_subgroup = new ArrayList<CandidateAcquisition>();
		List<CandidateAcquisition> simpleCandidateAcquisitions = new ArrayList<CandidateAcquisition>();

		int istart = 0;
		int istop  = 0;
		double proba = 0;
		for (int bidx = 0 ; bidx < borders.size() ; bidx++) {
			do {
				proba = simpleCandidateAcquisitionsPriority0.get(istop).acquisitionWindows.get(0).cloudProba;
				istop++;
			} while (proba < borders.get(bidx));
			istop  = istop - 1;
			if (istop == istart){
				current_subgroup = Collections.emptyList();
			} else {
				current_subgroup = simpleCandidateAcquisitionsPriority0.subList(istart,istop);
			}
			istart = istop;
			Collections.shuffle(current_subgroup);
			System.out.println(current_subgroup);
			simpleCandidateAcquisitions.addAll(current_subgroup);
		}

		while(!simpleCandidateAcquisitions.isEmpty()){

			CandidateAcquisition a = simpleCandidateAcquisitions.remove(0);



            // try to plan one acquisition window for this acquisition (and stop once a feasible acquisition window is found
            for(AcquisitionWindow acqWindow : a.acquisitionWindows){
				Satellite satellite = acqWindow.satellite;
				SatellitePlan satellitePlan = satellitePlans.get(satellite);
				satellitePlan.add(acqWindow);
				if(satellitePlan.isFeasible()){
					nPlanned++;
					a.selectedAcquisitionWindow = acqWindow;
					List <CandidateAcquisition> removeList = new ArrayList<CandidateAcquisition>();
					for(CandidateAcquisition acq : simpleCandidateAcquisitions){
						if (acq.name.equals(a.name)) {
							removeList.add(acq);
						}
					}
					simpleCandidateAcquisitions.removeAll(removeList);
				}
				else
					satellitePlan.remove(acqWindow);
			}
        }
		System.out.println("nPlanned: " + nPlanned + "/" + nCandidates);
	}


	private class SatellitePlan {

		/** Acquisitions to be realized by the satellite */
		private List<AcquisitionWindow> acqWindows;
		/** Map defining the start time of each acquisition in the solution schedule */
		private Map<AcquisitionWindow,Double> startTimes;


		public SatellitePlan(){
			acqWindows = new ArrayList<AcquisitionWindow>();
			startTimes = new HashMap<AcquisitionWindow,Double>();
		}

		public double getStart(AcquisitionWindow aw){
			return startTimes.get(aw);
		}

		public List<AcquisitionWindow> getAcqWindows(){
			return acqWindows;
		}

		public void add(AcquisitionWindow aw){
			acqWindows.add(aw);
		}

		public void remove(AcquisitionWindow aw){
			acqWindows.remove(aw);
			startTimes.remove(aw);
		}

		/**
		 * 
		 * @return true if the list of acquisition windows is evaluated as being feasible from a temporal point of view
		 */
		public boolean isFeasible(){

			// sort acquisition windows by increasing start times
			Collections.sort(acqWindows,startTimeComparator);

			// initialize the forward traversal of the acquisition windows by considering the first one 
			AcquisitionWindow prevAcqWindow = acqWindows.get(0);
			if(planningProblem.horizonStart > prevAcqWindow.latestStart)
				return false;		
			double startTime = Math.max(planningProblem.horizonStart,prevAcqWindow.earliestStart);
			startTimes.put(prevAcqWindow,startTime);
			double prevEndTime = startTime + prevAcqWindow.duration;

			// traverse all acquisition windows and check that each acquisition can be realized (taking into account roll angle transitions) 
			for(int i=1;i<acqWindows.size();i++){
				AcquisitionWindow acqWindow = acqWindows.get(i);
				double rollAngleTransitionTime = planningProblem.getTransitionTime(prevAcqWindow, acqWindow);
				startTime = Math.max(prevEndTime+rollAngleTransitionTime,acqWindow.earliestStart);
				if(startTime > acqWindow.latestStart) // sequence of acquisition windows not feasible
					return false;
				startTimes.put(acqWindow,startTime);
				prevEndTime = startTime + acqWindow.duration;
				prevAcqWindow = acqWindow;
			}		
			return true;
		}
	}

	/** Comparator used for sorting acquisition windows by increasing earliest start time */
	private final Comparator<AcquisitionWindow> startTimeComparator = new Comparator<AcquisitionWindow>(){
		@Override
		public int compare(AcquisitionWindow w0, AcquisitionWindow w1) {
			return Double.compare(w0.earliestStart, w1.earliestStart);
		}		
	};

	/**
	 * Write the acquisition plan of a given satellite in a file
	 * @param satellite
	 * @param solutionFilename
	 * @throws IOException
	 */
	public void writePlan(Satellite satellite, String solutionFilename) throws IOException{
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(solutionFilename, false)));
		SatellitePlan plan = satellitePlans.get(satellite);
		for(AcquisitionWindow aw : plan.getAcqWindows()){
			double start = plan.getStart(aw);
			writer.write(aw.candidateAcquisition.idx + " " + aw.idx + " " + start + " " + (start+aw.duration) + 
					 " " + aw.candidateAcquisition.name + "\n");
		}
		writer.flush();
		writer.close();
	}

	public void printScores(PlanningProblem pb){
		double prio_score = 0;
		double cloud_score = 0;
		for (Satellite sat : pb.satellites){
			SatellitePlan plan = satellitePlans.get(sat);
			for(AcquisitionWindow aw : plan.getAcqWindows()){
				prio_score  += 1 - aw.candidateAcquisition.priority;
				cloud_score += 1 - aw.cloudProba;

			}
		}
		System.out.println(prio_score);
		System.out.println(cloud_score);
	}

	
	public static void main(String[] args) throws XMLStreamException, FactoryConfigurationError, IOException{
		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		pb.printStatistics();
		AcquisitionPlannerGreedySimpleAcquisitions planner = new AcquisitionPlannerGreedySimpleAcquisitions(pb);
		planner.planAcquisitions();	
		for(Satellite satellite : pb.satellites){
			planner.writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
		}
		planner.printScores(pb);
	}
	
}
