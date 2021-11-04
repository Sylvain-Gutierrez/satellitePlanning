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

    public static List<Satellite> get_station_visi(Double time, Station station, PlanningProblem pb, List<DownloadPlanLine> plan, Double min_dl_time){
        
		List<Satellite> station_visi = new ArrayList<Satellite>();

		for (DownloadWindow d : pb.downloadWindows){
			
			if (time >= d.start 
				&& time <= d.end - min_dl_time
				&& d.station == station 
				&& is_sat_occupied(time, d.satellite, plan) == false
				&& !station_visi.contains(d.satellite)
				){
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
	public static Double get_min(Map<Station, Double> map){
        List<Station> keys = new ArrayList<>(map.keySet());
        Station key = keys.get(0);
        Double minimum = map.get(key);

        for (Station k : keys){
            if (map.get(k) < minimum){
                key = k;
                minimum = map.get(k);
            }
        }

        return minimum;
    }

	public static void planDownloads(SolutionPlan plan, String solutionFilename) throws IOException{

		final PlanningProblem pb = plan.pb;
		List<CandidateAcquisition> acqPlan = plan.plannedAcquisitions;

		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(solutionFilename, false)));

		boolean firstLine = true;

		List<DownloadPlanLine> downloadPlan = new ArrayList<DownloadPlanLine>();
        Map<Station, Double> stationCurrentTimes = new HashMap<Station,Double>();
		List<Acquisition> alreadyDownloaded = new ArrayList<Acquisition>();

		Double min_dl_time = 100000.;
		for (RecordedAcquisition acq : pb.recordedAcquisitions){
			Double tt = acq.getVolume()/Params.downlinkRate;
			if (tt < min_dl_time){
				min_dl_time = tt;
			}
		}
		for(CandidateAcquisition acq : acqPlan){
			Double tt = acq.getVolume()/Params.downlinkRate;
			if (tt < min_dl_time){
				min_dl_time = tt;
			}
		}

        for (Station station : pb.stations){
            stationCurrentTimes.put(station, 1e42);
        }

        for (DownloadWindow dlw : pb.downloadWindows){
            if (dlw.start < stationCurrentTimes.get(dlw.station)){
                stationCurrentTimes.put(dlw.station,dlw.start);
            }
        }
        
        final Double end_Time = 3600 * Double.parseDouble(Params.horizon.substring(0,Params.horizon.length()-1));
		DownloadWindow currentWindow = pb.downloadWindows.get(0);

        while (get_min(stationCurrentTimes) < end_Time){

            Station currentStation = get_min_key(stationCurrentTimes);
			final Double currentTime = stationCurrentTimes.get(currentStation);
			List<Satellite> station_visi = get_station_visi(currentTime, currentStation, pb, downloadPlan, min_dl_time);

			Collections.sort(station_visi, new Comparator<Satellite>(){
				public int compare(Satellite s1,Satellite s2){
						return (int) (get_sat_visi(currentTime, s1, pb) - get_sat_visi(currentTime, s2, pb));
				}});

			boolean new_download_done = false;
			for (Satellite sat : station_visi){
				// get all recorded acquisitions associated with this satellite
				List<Acquisition> candidateDownloads = new ArrayList<Acquisition>();
				
				// System.out.println("Squalala nous sommes partis");
				for(RecordedAcquisition dl : pb.recordedAcquisitions){
					if(dl.satellite == sat){
						candidateDownloads.add(dl);
					}
				}
				// System.out.println(candidateDownloads.size());

				// get all planned acquisitions associated with this satellite
				for(CandidateAcquisition a : acqPlan){
					if(a.selectedAcquisitionWindow.satellite == sat && a.getAcquisitionTime() < currentTime)
						candidateDownloads.add(a);
				}
				// System.out.println(candidateDownloads.size());

				// remove already downloaded acquisition on this satellite
				for (Acquisition alreadyDownloadedAcquisition : alreadyDownloaded){
					if (candidateDownloads.contains(alreadyDownloadedAcquisition)) {
						candidateDownloads.remove(alreadyDownloadedAcquisition);
					}
				}
				// System.out.println(candidateDownloads.size());

				if (candidateDownloads.size() > 0){
					// sort acquisitions by priority then start time
					Collections.sort(candidateDownloads,new Comparator<Acquisition>(){
						public int compare(Acquisition a1, Acquisition a2){
								return (int) (1e10 * (a1.priority - a2.priority) + (a1.getAcquisitionTime() - a2.getAcquisitionTime()));
						}});
				
					Acquisition downloadAcquisition = candidateDownloads.remove(0);
					for (DownloadWindow dlw : pb.downloadWindows){
						if (dlw.start <= currentTime && dlw.end > currentTime && dlw.satellite == downloadAcquisition.getSatellite() && dlw.station == currentStation){
							currentWindow = dlw;
						}
					}
					
					Double dlDuration = downloadAcquisition.getVolume()/Params.downlinkRate;


					if (currentTime+dlDuration <= currentWindow.end){
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
					else {
						System.out.println("Acquisition not downloaded, times up.");
					}
				}


			}
			if (new_download_done == false) {
				stationCurrentTimes.put(currentStation, currentTime + Params.waitingTime);
				// System.out.println("Waiting on station " + currentStation.name);
				if (stationCurrentTimes.get(currentStation) >= currentWindow.end){
					Double local_Time = end_Time;
					Boolean jump_done = false;
					for (DownloadWindow dlw : pb.downloadWindows){
						if (dlw.start > currentTime && dlw.start < local_Time && dlw.station == currentStation){
							local_Time = dlw.start;
							jump_done = true;
						}
					}
					if (jump_done == true) {
						stationCurrentTimes.put(currentStation, local_Time);
						System.out.println("Waiting until next DLwindow on station " + currentStation.name);
					}
					else {
						stationCurrentTimes.put(currentStation, end_Time);
						System.out.println("Waiting until the end on station " + currentStation.name);
					}
				}
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
