package utils;

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
import java.util.Random;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import params.Params;
import problem.AcquisitionWindow;
import problem.CandidateAcquisition;
import problem.PlanningProblem;
import problem.ProblemParserXML;
import problem.Satellite;
import solver.AcquisitionPlannerGreedyPriority;
import solver.AcquisitionPlannerGreedySimpleAcquisitions;
import solver.BadAcquisitionPlannerGreedy;


public class StrategiesTest {
    

	public static void main(String[] args) throws XMLStreamException, FactoryConfigurationError, IOException{
		ProblemParserXML parser = new ProblemParserXML(); 
		PlanningProblem pb = parser.read(Params.systemDataFile,Params.planningDataFile);
		pb.printStatistics();

		// RUN ACQUISITION PLANNERS
		List<Object> planners = new ArrayList<Object>();

		planners.add(new BadAcquisitionPlannerGreedy(pb));
		planners.add(new AcquisitionPlannerGreedyPriority(pb));
		planners.add(new AcquisitionPlannerGreedySimpleAcquisitions(pb));

		
		// for(Satellite satellite : pb.satellites){
		// 	planners.get(0).writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
		// 	planners.get(1).writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
		// 	planners.get(2).writePlan(satellite, "output/solutionAcqPlan_"+satellite.name+".txt");
		// }
		System.out.println("Acquisition planning done");
	}
	
}