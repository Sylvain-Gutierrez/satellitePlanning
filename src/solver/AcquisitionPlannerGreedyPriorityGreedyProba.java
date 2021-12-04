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
import java.util.Map;

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
 * @author cpralet
 *
 */
public class AcquisitionPlannerGreedyPriorityGreedyProba {

	/** Planning problem for which this acquisition planner is used */
	private final PlanningProblem planningProblem;
	/** Data structure used for storing the plan of each satellite */
	private final Map<Satellite,SatellitePlan> satellitePlans;

	
	/**
	 * Build an acquisition planner for a planning problem
	 * @param planningProblem
	 */
	public AcquisitionPlannerGreedyPriorityGreedyProba(PlanningProblem planningProblem){
		this.planningProblem = planningProblem;
		satellitePlans = new HashMap<Satellite,SatellitePlan>();
		for(Satellite satellite : planningProblem.satellites){
			satellitePlans.put(satellite, new SatellitePlan());
		}
	}

	/**
	 * Planning function which uses a greedy algorithm. The latter tries to plan at each step 
	 * one additional acquisition (randomly chosen), while there are candidate acquisitions left.
	 */
	public void planAcquisitions(){
		
		List<CandidateAcquisition> temp_candidateAcquisitions = new ArrayList<CandidateAcquisition>(planningProblem.candidateAcquisitions);
        int nCandidates = temp_candidateAcquisitions.size();
		int nPlanned = 0;
        
        // Comparator used to sort the list

		List<CandidateAcquisition> candidateAcquisitions = new ArrayList<CandidateAcquisition>();
		List<CandidateAcquisition> candidateAcquisitionsPriority0 = new ArrayList<CandidateAcquisition>();
		List<CandidateAcquisition> candidateAcquisitionsPriority1 = new ArrayList<CandidateAcquisition>();
        
		for (CandidateAcquisition acq : temp_candidateAcquisitions){
			if (acq.priority == 0){
				candidateAcquisitionsPriority0.add(acq);
			}
			if (acq.priority == 1){
				candidateAcquisitionsPriority1.add(acq);
			}
		}
		
		Collections.shuffle(candidateAcquisitionsPriority0);
		Collections.shuffle(candidateAcquisitionsPriority1);
		candidateAcquisitions.addAll(candidateAcquisitionsPriority0);
		candidateAcquisitions.addAll(candidateAcquisitionsPriority1);

		while(!candidateAcquisitions.isEmpty()){
            // select one candidate acquisition by priority and cloudProba
            
            // randomize in order to test the solver:
            //Collections.shuffle(candidateAcquisitions);

            CandidateAcquisition a = candidateAcquisitions.remove(0);                
                
            // try to plan one acquisition window for this acquisition (and stop once a feasible acquisition window is found
			
			if (a.acquisitionWindows.size() > 0){
				boolean martin = false;
				AcquisitionWindow feasible = a.acquisitionWindows.get(0);
				for(AcquisitionWindow acqWindow : a.acquisitionWindows){
					// feasible = acqWindow;

					Satellite satellite = acqWindow.satellite;
					SatellitePlan satellitePlan = satellitePlans.get(satellite);
					satellitePlan.add(acqWindow);

					if(satellitePlan.isFeasible()){
						if (martin == false) {
							martin = true;
						}
						else{
							if (acqWindow.cloudProba < feasible.cloudProba){
								feasible = acqWindow;
							}
						}
						
					}
					satellitePlan.remove(acqWindow);

					// if(satellitePlan.isFeasible()){
					// 	nPlanned++;
					// 	a.selectedAcquisitionWindow = acqWindow;
					// 	break;
					// }
					// else
					// 	satellitePlan.remove(acqWindow);
				}

				if (martin != false){
					// Collections.sort(feasibles,new Comparator<AcquisitionWindow>(){
					// 	public int compare(AcquisitionWindow a1,AcquisitionWindow a2){
					// 		  return (int) (1000*(a1.cloudProba - a2.cloudProba));
					// 	}});

					// AcquisitionWindow acqWindow0 = feasibles.get(0);
					Satellite satellite = feasible.satellite;
					SatellitePlan satellitePlan = satellitePlans.get(satellite);
					satellitePlan.add(feasible);
					nPlanned++;
					a.selectedAcquisitionWindow = feasible;
				}
			}
			
			// else{
			// 	System.out.println("BlzpifnkzjhbfsJDFN QKSJENF OQLIZNJFQUERJ");
			// }

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
		// double prio_score = 0;
		double cloud_score = 0;
		int size = 0;
		for (Satellite sat : pb.satellites){
			SatellitePlan plan = satellitePlans.get(sat);
			int local_size = plan.getAcqWindows().size();
			for(AcquisitionWindow aw : plan.getAcqWindows()){
				// prio_score  += 1 - aw.candidateAcquisition.priority;
				cloud_score += aw.cloudProba;
			}
			size += local_size;
		}
		System.out.println("Mean cloud probability : ");
		System.out.println(cloud_score/size);
	}

	
	public static void main(String[] args) throws XMLStreamException, FactoryConfigurationError, IOException{
		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		pb.printStatistics();
		AcquisitionPlannerGreedyPriorityGreedyProba planner = new AcquisitionPlannerGreedyPriorityGreedyProba(pb);
		planner.planAcquisitions();	
		for(Satellite satellite : pb.satellites){
			planner.writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
		}
		planner.printScores(pb);
	}
	
}
