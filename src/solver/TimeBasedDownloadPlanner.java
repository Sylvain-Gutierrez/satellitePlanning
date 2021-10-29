package solver;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import params.Params;
import problem.Acquisition;
import problem.CandidateAcquisition;
import problem.DownloadWindow;
import problem.PlanningProblem;
import problem.RecordedAcquisition;
import problem.ProblemParserXML;
import problem.Satellite;
import problem.Station;

/**
 * Class implementing a download planner which tries to insert downloads into the plan
 * by ordering acquisitions following an increasing order of their realization time, and by
 * considering download windows chronologically 
 * @author cpralet
 *
 */

public class TimeBasedDownloadPlanner {

    public static List<Satellite> get_station_visi(Double time, Station station, PlanningProblem pb, List<DownloadPlanLine> plan){
        
		List<Satellite> station_visi = new ArrayList<Satellite>();

		for (DownloadWindow d : pb.downloadWindows){
			if (time >= d.start 
				&& time <= d.end 
				&& d.station == station 
				&& is_sat_occupied(time, d.satellite, plan) == false
				&& !station_visi.contains(d.satellite)){
				station_visi.add(d.satellite);
			}
		}

		return station_visi;
    }
    
    public static int get_sat_visi(Double time, Satellite sat, PlanningProblem pb){
        
		int sat_visi = 0;
		List<Station> list_stations = new ArrayList<Station>();

		for (DownloadWindow d : pb.downloadWindows){
			if (time >= d.start && time <= d.end && d.satellite == sat && !list_stations.contains(d.station)){
				sat_visi += 1;
				list_stations.add(d.station);
			}
		}

		return sat_visi;
    }

    public static Boolean is_sat_occupied(Double time, Satellite sat, List<DownloadPlanLine> plan){
        for (DownloadPlanLine line : plan){
			if (time >= line.start && time <= line.end && line.satellite == sat) {
				return true;
			}
		}

		return false;
    }




    
    public static Station get_min_key(Map<Station, Double> map){
        List<Station> keys = new ArrayList<>(map.keySet());
        Station key = keys.get(0);
        Double minimum = map.get(key);

        for (Station k : keys){
            if (map.get(k) < minimum){
                key = k;
                minimum = map.get(k);
            }
        }

        return key;
    }

	public static void planDownloads(SolutionPlan plan, String solutionFilename) throws IOException{

		final PlanningProblem pb = plan.pb;
		List<CandidateAcquisition> acqPlan = plan.plannedAcquisitions;

		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(solutionFilename, false)));

		boolean firstLine = true;

		List<DownloadPlanLine> downloadPlan = new ArrayList<DownloadPlanLine>();
        Map<Station, Double> stationCurrentTimes = new HashMap<Station,Double>();
		List<Acquisition> alreadyDownloaded = new ArrayList<Acquisition>();

        for (Station station : pb.stations){
            stationCurrentTimes.put(station, 1e42);
        }

        for (DownloadWindow dlw : pb.downloadWindows){
            if (dlw.start < stationCurrentTimes.get(dlw.station)){
                stationCurrentTimes.put(dlw.station,dlw.start);
            }
        }
        
        

        while (true){

            Station currentStation = get_min_key(stationCurrentTimes);
			final Double currentTime = stationCurrentTimes.get(currentStation);
			DownloadWindow currentWindow = pb.downloadWindows.get(0);
			for (DownloadWindow dlw : pb.downloadWindows){
				if (dlw.start < currentTime && dlw.end > currentTime && dlw.station == currentStation){
					currentWindow = dlw;
				}
			}
			List<Satellite> station_visi = get_station_visi(currentTime, currentStation, pb, downloadPlan);

			Collections.sort(station_visi, new Comparator<Satellite>(){
				public int compare(Satellite s1,Satellite s2){
						return (int) (get_sat_visi(currentTime, s1, pb) - get_sat_visi(currentTime, s2, pb));
				}});

			boolean new_download_done = false;
			for (Satellite sat : station_visi){
				// get all recorded acquisitions associated with this satellite
				List<Acquisition> candidateDownloads = new ArrayList<Acquisition>();
				for(RecordedAcquisition dl : pb.recordedAcquisitions){
					if(dl.satellite == sat){
						candidateDownloads.add(dl);
					}
				}
				// get all planned acquisitions associated with this satellite
				for(CandidateAcquisition a : acqPlan){
					if(a.selectedAcquisitionWindow.satellite == sat && a.getAcquisitionTime() < currentTime)
						candidateDownloads.add(a);
				}
				// remove already downloaded acquisition on this satellite
				for (Acquisition alreadyDownloadedAcquisition : alreadyDownloaded){
					if (candidateDownloads.contains(alreadyDownloadedAcquisition)) {
						candidateDownloads.remove(alreadyDownloadedAcquisition);
					}
				}

				if (candidateDownloads.size() > 0){
					// sort acquisitions by priority then start time
					Collections.sort(candidateDownloads,new Comparator<Acquisition>(){
						public int compare(Acquisition a1, Acquisition a2){
								return (int) (1e10 * (a1.priority - a2.priority) + (a1.getAcquisitionTime() - a2.getAcquisitionTime()));
						}});
				
					Acquisition downloadAcquisition = candidateDownloads.remove(0);
					Double dlDuration = downloadAcquisition.getVolume()/Params.downlinkRate;
					new_download_done = true;

					stationCurrentTimes.put(currentStation, currentTime + dlDuration);

					alreadyDownloaded.add(downloadAcquisition);
					DownloadPlanLine line = new DownloadPlanLine(sat, 
																currentStation, 
																currentTime, 
																currentTime+dlDuration, 
																downloadAcquisition);
					downloadPlan.add(line);

					if(firstLine){
						firstLine = false;
					}
					else 
						writer.write("\n");
					if(downloadAcquisition instanceof RecordedAcquisition)
						writer.write("REC " + ((RecordedAcquisition) downloadAcquisition).idx + " " + currentWindow.idx + " " + currentTime + " " + (currentTime+dlDuration));
					else // case CandidateAcquisition
						writer.write("CAND " + ((CandidateAcquisition) downloadAcquisition).idx + " " + currentWindow.idx + " " + currentTime + " " + (currentTime+dlDuration));

					break;
				}


			}
			if (new_download_done == false) {
				stationCurrentTimes.put(currentStation, currentTime + Params.waitingTime);
				System.out.println("Waiting ... ");
			}



            break;
        }

























































		for(Satellite satellite : pb.satellites){
			// get all recorded acquisitions associated with this satellite
			List<Acquisition> candidateDownloads = new ArrayList<Acquisition>();
			for(RecordedAcquisition dl : pb.recordedAcquisitions){
				if(dl.satellite == satellite)
					candidateDownloads.add(dl);
			}
			// get all planned acquisitions associated with this satellite
			for(CandidateAcquisition a : acqPlan){
				if(a.selectedAcquisitionWindow.satellite == satellite)
					candidateDownloads.add(a);
			}
			// sort acquisitions by increasing start time
			Collections.sort(candidateDownloads, new Comparator<Acquisition>(){
				@Override
				public int compare(Acquisition a0, Acquisition a1) {
					double start0 = a0.getAcquisitionTime(); 
					double start1 = a1.getAcquisitionTime();
					if(start0 < start1)
						return -1;
					if(start0 > start1)
						return 1;
					return 0;
				}

			});

			// sort download windows by increasing start time
			List<DownloadWindow> downloadWindows = new ArrayList<DownloadWindow>();
			for(DownloadWindow w : pb.downloadWindows){
				if(w.satellite == satellite)
					downloadWindows.add(w);
			}
			Collections.sort(downloadWindows, new Comparator<DownloadWindow>(){
				@Override
				public int compare(DownloadWindow a0, DownloadWindow a1) {
					double start0 = a0.start; 
					double start1 = a1.start;
					if(start0 < start1)
						return -1;
					if(start0 > start1)
						return 1;
					return 0;
				}
			});			
			if(downloadWindows.isEmpty())
				continue;

			// chronological traversal of all download windows combined with a chronological traversal of acquisitions which are candidate for being downloaded
			int currentDownloadWindowIdx = 0;
			DownloadWindow currentWindow = downloadWindows.get(currentDownloadWindowIdx);
			double currentTime = currentWindow.start;
			for(Acquisition a : candidateDownloads){
				currentTime = Math.max(currentTime, a.getAcquisitionTime());
				double dlDuration = a.getVolume() / Params.downlinkRate;
				while(currentTime + dlDuration > currentWindow.end){					
					currentDownloadWindowIdx++;
					if(currentDownloadWindowIdx < downloadWindows.size()){
						currentWindow = downloadWindows.get(currentDownloadWindowIdx);
						currentTime = Math.max(currentTime, currentWindow.start);
					}
					else
						break;
				}
				
				if(currentDownloadWindowIdx >= downloadWindows.size())
					break;

				if(firstLine){
					firstLine = false;
				}
				else 
					writer.write("\n");
				if(a instanceof RecordedAcquisition)
					writer.write("REC " + ((RecordedAcquisition) a).idx + " " + currentWindow.idx + " " + currentTime + " " + (currentTime+dlDuration));
				else // case CandidateAcquisition
					writer.write("CAND " + ((CandidateAcquisition) a).idx + " " + currentWindow.idx + " " + currentTime + " " + (currentTime+dlDuration));
				currentTime += dlDuration;
			}
		}
		writer.flush();
		writer.close();
	}

	
	public static void main(String[] args) throws XMLStreamException, FactoryConfigurationError, IOException, ParseException{
		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		SolutionPlan plan = new SolutionPlan(pb);
		int nSatellites = pb.satellites.size();
		for(int i=1;i<=nSatellites;i++)
			plan.readAcquisitionPlan("output/solutionAcqPlan_SAT"+i+".txt");			
		planDownloads(plan,"output/downloadPlan.txt");		
				
	}
	
}
