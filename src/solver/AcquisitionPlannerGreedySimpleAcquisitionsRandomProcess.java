package solver;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
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
	/** Data structure used to store sorted acquisition before the random process is run */
	private List<List<CandidateAcquisition>> all_subgroups;

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

		// Make simple acquisitions (with single associated window) and separate them in lists associated with priority
		List<CandidateAcquisition> simple_candidate_acquisitions_priority0 = new ArrayList<CandidateAcquisition>();
		List<CandidateAcquisition> simple_candidate_acquisitions_priority1 = new ArrayList<CandidateAcquisition>();
		for(CandidateAcquisition acq : planningProblem.candidateAcquisitions){
				for(AcquisitionWindow w : acq.acquisitionWindows){
					CandidateAcquisition temp_acq = new CandidateAcquisition(acq.name, acq.user, acq.priority, acq.longitude, acq.latitude, acq.idx);
					temp_acq.acquisitionWindows.add(w);
					if (acq.priority == 0){
						simple_candidate_acquisitions_priority0.add(temp_acq);
					} else {
						simple_candidate_acquisitions_priority1.add(temp_acq);
					}
				}
			}


		// Sort the lists (prio0 and prio1) wrt cloud probability
		class CloudProbaComparator implements Comparator<CandidateAcquisition> {
			public int compare(CandidateAcquisition a1, CandidateAcquisition a2) {
				double p1 = a1.acquisitionWindows.get(0).cloudProba;
				double p2 = a2.acquisitionWindows.get(0).cloudProba;
				if (p1 == p2)
					return 0;
				else if (p1 > p2)
					return 1;
				else
					return -1;
			}
		}
		Collections.sort(simple_candidate_acquisitions_priority0,new CloudProbaComparator());
		Collections.sort(simple_candidate_acquisitions_priority1,new CloudProbaComparator());
		List<List<CandidateAcquisition>> all_priorities_acquisitions = new ArrayList<>(); 
		all_priorities_acquisitions.add(simple_candidate_acquisitions_priority0);
		all_priorities_acquisitions.add(simple_candidate_acquisitions_priority1);

		/////////////
		// Separate the lists in subgroups of close importances:
		/////////////

		this.all_subgroups = new ArrayList<>(); 
		List<Double> borders = Arrays.asList(Params.probabilityBorders);
		
		int n_priorities = all_priorities_acquisitions.size();
		int priorities_left = n_priorities;
		while (priorities_left > 0){
			List<CandidateAcquisition> current_list = all_priorities_acquisitions.get(n_priorities - priorities_left);
			List<CandidateAcquisition> current_subgroup = new ArrayList<CandidateAcquisition>();
			
			// for each threshold of cloudProba defined in borders (list of probabilities), ...
			int istart   = 0;
			int istop    = 0;
			double proba = 0.0;
			for (double proba_threshold : borders){
				int simpleAcqSize = current_list.size();
				// ... locate the corresponding subgroup of acquisitions/windows, ...
				boolean still_in_subgroup = true;
				while (still_in_subgroup){
					proba = current_list.get(istop).acquisitionWindows.get(0).cloudProba;
					if (proba <= proba_threshold){
						if (istop < simpleAcqSize-1){
							istop++;
						} else {still_in_subgroup = false;}
					} else {still_in_subgroup = false;}
				}
				// ... and extract the close cloud probabilities between this threshold and the previous one.
				if (istop == istart){
					current_subgroup = Collections.emptyList();
				} else {
					current_subgroup = current_list.subList(istart,istop);
				}
				this.all_subgroups.add(current_subgroup);
				istart = istop;
			}
			
			//////////////////////////
			priorities_left -= 1;
		}
	}

	public void planAcquisitions(){ // RUN THE RANDOM PROCESS A SINGLE TIME
		/**
		 * Planning function which uses a greedy algorithm.
		 * Candidate acquisitions were transformed (in the constructor) into simple acquisitions,
		 * i.e. associated with a single acquisition window.
		 * This allows to sort these acquisitions based on their priority AND cloud probability.
		 * Subgroups of acquisitions with relatively close importance (=cloud probability, in a list
		 * of constant priority) are made and then shuffled. This allows to keep the
		 * best candidates first and introduce a random process to find a solution that maximizes
		 * the number of acquisitions planned by running it several times.
		 * 
		 * This function runs the random process.
		 * 
		 */
		
		List<CandidateAcquisition> simple_candidate_acquisitions = new ArrayList<CandidateAcquisition>();

		// Make an instance of the candidates list
		for (List<CandidateAcquisition> subgroup : this.all_subgroups) {
			Collections.shuffle(subgroup);
			simple_candidate_acquisitions.addAll(subgroup);
		}
		
		while(!simple_candidate_acquisitions.isEmpty()){

			// for each distinct acquisition in the sorted and randomized list, pick the most advantageous window
			CandidateAcquisition current_acq = simple_candidate_acquisitions.remove(0);
			
			// try to plan the acquisition with this window.
			for(AcquisitionWindow acq_window : current_acq.acquisitionWindows){
				Satellite satellite = acq_window.satellite;
				SatellitePlan satellitePlan = satellitePlans.get(satellite);
				satellitePlan.add(acq_window);
				if(satellitePlan.isFeasible()){
					current_acq.selectedAcquisitionWindow = acq_window;
					// Remove duplicates (simple acquisitions created from the same as current_acq)
					List <CandidateAcquisition> removeList = new ArrayList<CandidateAcquisition>();
					for(CandidateAcquisition acq : simple_candidate_acquisitions){
						if (acq.idx == current_acq.idx) {
							removeList.add(acq);
						}
					}
					simple_candidate_acquisitions.removeAll(removeList);
				}
				else {satellitePlan.remove(acq_window);}
			}
		}
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

	public class Score {
		int run;
		double planned_fraction;
		double mean_priority;
		double mean_cloud_proba;
		public Score(int run){
			this.run = run;
			this.planned_fraction = 0.;
			this.mean_priority = 1000.;
			this.mean_cloud_proba = 1000.; // unreachable bad scores
		}

		public String toString(){
			return "----------------------   Run: " + this.run + "   ----------------------"
				+ "\n  Fraction of acquisitons planned: "+ this.planned_fraction*100+" %"
				+ "\n  Mean priority reached:           "+ this.mean_priority
				+ "\n  Mean cloud probability reached:  "+ this.mean_cloud_proba
				+ "\n--------------------------------------------------------";
		}
	}
	public Score getScore(PlanningProblem pb, int run){
		/** A simple counter for several performance metrics:
		 * - the number of acquisitions successfully planned
		 * - the mean priority. The lower the better
		 * - the mean cloud probabilty. The lower the better
		 */
		double number_planned = 0.0;
		double sum_priority = 0.0;
		double sum_cloud_proba = 0.0;
		for (Satellite sat : pb.satellites){
			SatellitePlan plan = satellitePlans.get(sat);
			for(AcquisitionWindow aw : plan.getAcqWindows()){
				number_planned  += 1;
				sum_priority    += aw.candidateAcquisition.priority;
				sum_cloud_proba += aw.cloudProba;
			}
		}
		
		Score score = new Score(run);
		score.planned_fraction = number_planned/pb.candidateAcquisitions.size();
		score.mean_priority = sum_priority/number_planned;
		score.mean_cloud_proba = sum_cloud_proba/number_planned;
		return score;
	}

	public boolean isBetterThan(Score s1, Score s2) {
		double delta_plan  =   s1.planned_fraction - s2.planned_fraction;  // positive if s1 better than s2
		double delta_prio  = -(s1.mean_priority    - s2.mean_priority);    // same
		double delta_cloud = -(s1.mean_cloud_proba - s2.mean_cloud_proba); // same

		// The following metric should be tuned.
		double total_delta = delta_plan + delta_prio + delta_cloud; // lies between -3.0 and 3.0

		return (total_delta > 0.0);
	}

	public static void main(String[] args) throws XMLStreamException, FactoryConfigurationError, IOException{
		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		pb.printStatistics();

		AcquisitionPlannerGreedySimpleAcquisitionsRandomProcess planner = new AcquisitionPlannerGreedySimpleAcquisitionsRandomProcess(pb);
		
		List<Score> all_scores = new ArrayList<Score>();
		Score best_score = planner.new Score(-1);
		Score current_score;

		for (int current_run = 0; current_run < Params.n_runs; current_run++) {
			planner = new AcquisitionPlannerGreedySimpleAcquisitionsRandomProcess(pb);
			planner.planAcquisitions();	
			current_score = planner.getScore(pb, current_run);
			all_scores.add(current_score);
			if (planner.isBetterThan(current_score, best_score)) {
				for(Satellite satellite : pb.satellites){
					planner.writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
				}
				best_score = current_score;
				System.out.println("New best score:\n"+current_score);
			} else {
				System.out.println("Run "+ current_score.run +": Best score not beaten.");
			}
		}

	}
}
