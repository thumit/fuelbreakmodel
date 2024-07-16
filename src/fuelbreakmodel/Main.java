package fuelbreakmodel;

import java.awt.EventQueue;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Stream;

import ilog.concert.IloLPMatrix;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Status;

public class Main {
	private static Main main;
	private DecimalFormat twoDForm = new DecimalFormat("#.##");	 // Only get 2 decimal
	private DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
	private long time_start, time_end;
	private double time_reading, time_solving, time_writing;

	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				main = new Main();
			}
		});
	}
	
	public Main() {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				boolean export_problem_file = false;
				boolean export_solution_file = false;
				double optimality_gap = 0.000000001;		// set relative gap (Ep) to 0.000000001 is the trick achieve final solution gap 0. But try to set 0 first to see if it works
				String test_case_description = "Test case 2.2";
				double budget = 19640.99;
				boolean excluding_largest_fires = false;
				
				// For the Great Basin data - 2 inputs needed
				String source_folder = get_workingLocation().replace("fuelbreakmodel", "");
//				File input_1_file = new File(source_folder + "/model_inputs/Manuscript 19/GB_attribute_table_2_fires_example.txt");
				File input_1_file = new File(source_folder + "/model_inputs/Manuscript 19/GB_attribute_table_final.txt");
				File input_2_file = new File(source_folder + "/model_inputs/Manuscript 19/GB_attribute_table_and_fuel_breaks_core.txt");
				String output_folder = source_folder + "/model_outputs/Manuscript 19/" + test_case_description;
				File outputFolderFile = new File(output_folder);
				if (!outputFolderFile.exists()) outputFolderFile.mkdirs(); 	// Create output folder and its parents if they don't exist
				File problem_file = new File(output_folder + "/problem.lp");
				File solution_file = new File(output_folder + "/solution.sol");
				File output_solution_summary_file = new File(output_folder + "/output_1_solution_summary.txt");
				File output_fuelbreaks_decisions_file = new File(output_folder + "/output_2_fuelbreaks_decisions.txt");
				File output_all_variables_file = new File(output_folder + "/output_3_all_variables.txt");
				File output_nonzero_variables_file = new File(output_folder + "/output_4_nonzero_variables.txt");
				GreatBasin_2_inputs_data_processing data_processing = new GreatBasin_2_inputs_data_processing(input_1_file, input_2_file, excluding_largest_fires);
							
				// Read all inputs and get information--------------------------------------------------------------------------------------------
				int number_of_fires = data_processing.get_number_of_fires();		// number of fires
				int[] original_fire_id = data_processing.get_original_fire_id(); 	// store the original fire_id: which is "fire_id" in the input_01_file of the example, or "FIRE_NUMBE" in the great basin attribute table
				int[] number_of_PODS = data_processing.get_number_of_PODS(); 		// number of dynamic PODs for each fire
				int[] n = data_processing.get_n(); 									// number of dynamic PODs for each fire minus one
				int[] ignition_POD = data_processing.get_ignition_POD(); 			// the ignition POD of each fire (always 1 as it is result of running the python script)
															
				double[][] core_areas = data_processing.get_core_areas();					// w(e,ie) or ENVC in the objective function, with e is the FireID, i is the dynamic POD				
				List<Integer>[][] adjacent_PODS = data_processing.get_adjacent_PODS();		// Adjacent PODs
								
				int number_of_fuelbreaks = data_processing.get_number_of_fuelbreaks();
				int number_of_management_options = data_processing.get_number_of_management_options(); 		// either 0, 1, 2, 3, 4 associated with break's width of 0, 100, 200, 300, 400 feet
				double[] break_length = data_processing.get_break_length();
				double[][] break_area = data_processing.get_break_area();	// maintenance area of a fuel break when a management option is implemented
				double[][] q = data_processing.get_q();		// the flame length capacity of a fuel break when a management option is implemented
				double[][] c = data_processing.get_c(); 	// the cost of a fuel break when a management option is implemented

				List<Integer>[][][] b_list = data_processing.get_b_list();		// b_list stores all the breaks within the shared boundary of 2 adjacent polygons i and j of fire e
				List<Double>[][][] fl_list = data_processing.get_fl_list();		// fl_list stores flame lengths across all the break segments within the shared boundary of 2 adjacent polygons i and j of fire e
				
				
				// DEFINITIONS --------------------------------------------------------------
				// DEFINITIONS --------------------------------------------------------------
				// DEFINITIONS --------------------------------------------------------------
				List<Information_Variable> var_info_list = new ArrayList<Information_Variable>();
				List<Double> objlist = new ArrayList<Double>();					// objective coefficient
				List<String> vnamelist = new ArrayList<String>();				// variable name
				List<Double> vlblist = new ArrayList<Double>();					// lower bound
				List<Double> vublist = new ArrayList<Double>();					// upper bound
				List<IloNumVarType> vtlist = new ArrayList<IloNumVarType>();	// variable type
				int nvars = 0;

				// declare arrays to keep variables. some variables are optimized by using jagged-arrays (xE, xR, fire)
				int[][] D = null;	// D(b,k) with b is the break id, k is the management option
				D = new int[number_of_fuelbreaks][];
				for (int b = 0; b < number_of_fuelbreaks; b++) {
					D[b] = new int[number_of_management_options];	// 4 management option either 0, 1, 2, 3 associated with break's width of 100, 200, 300, 400 feet
					for (int k = 0; k < number_of_management_options; k++) {
						int fuelbreak_ID = b + 1;
						int management_option = k + 1;
						String var_name = "D_" + fuelbreak_ID + "_" + management_option;
						Information_Variable var_info = new Information_Variable(var_name);
						var_info_list.add(var_info);
						objlist.add((double) 0);
						vnamelist.add(var_name);
//						if (k == 3) {
//							vlblist.add((double) 1);
//							vublist.add((double) 1);
//						} else {
//							vlblist.add((double) 0);
//							vublist.add((double) 0);
//						}
						vlblist.add((double) 0);
						vublist.add((double) 1);
						vtlist.add(IloNumVarType.Bool);
						D[b][k] = nvars;
						nvars++;
					}
				}
				
				int[][] X = null;	// X(e,ie) with e is the FireID, i is the dynamic POD
				X = new int[number_of_fires][];
				for (int e = 0; e < number_of_fires; e++) {
					X[e] = new int[number_of_PODS[e]];
					for (int i = 0; i < number_of_PODS[e]; i++) {
						int fire_ID = original_fire_id[e];
						int POD_ID = i + 1;
						String var_name = "X_" + fire_ID + "_" + POD_ID;
						Information_Variable var_info = new Information_Variable(var_name);
						var_info_list.add(var_info);
						objlist.add((double) core_areas[e][i]);
						vnamelist.add(var_name);
						vlblist.add((double) 0);
						vublist.add((double) 1);
						vtlist.add(IloNumVarType.Bool);
						X[e][i] = nvars;
						nvars++;
					}
				}
				
				int[] Q = new int[number_of_fuelbreaks];	// Q(b)	is the upper bound flame length for the fuel break b to sustain
				for (int b = 0; b < number_of_fuelbreaks; b++) {
					int fuelbreak_ID = b + 1;
					String var_name = "Q_" + fuelbreak_ID;
					Information_Variable var_info = new Information_Variable(var_name);
					var_info_list.add(var_info);
					objlist.add((double) 0);
					vnamelist.add(var_name);
					vlblist.add((double) 0);
					vublist.add(Double.MAX_VALUE);
					vtlist.add(IloNumVarType.Float);
					Q[b] = nvars;
					nvars++;
				}
				
				int[] A = new int[number_of_fuelbreaks];	// A(b)	is maintenance area associated with break b
				for (int b = 0; b < number_of_fuelbreaks; b++) {
					int fuelbreak_ID = b + 1;
					String var_name = "A_" + fuelbreak_ID;
					Information_Variable var_info = new Information_Variable(var_name);
					var_info_list.add(var_info);
					objlist.add((double) 0);
					vnamelist.add(var_name);
					vlblist.add((double) 0);
					vublist.add(Double.MAX_VALUE);
					vtlist.add(IloNumVarType.Float);
					A[b] = nvars;
					nvars++;
				}
							
				int[][][] Y = null;	// Y(e,ie,je) with e is the FireID, i and j are the dynamic POD
				Y = new int[number_of_fires][][];
				for (int e = 0; e < number_of_fires; e++) {
					Y[e] = new int[number_of_PODS[e] - 1][];
					for (int i = 0; i < number_of_PODS[e] - 1; i++) {
						Y[e][i] = new int[number_of_PODS[e]];
						for (int j = i + 1; j < number_of_PODS[e]; j++) {
							if (adjacent_PODS[e][i].contains(j)) {
								int fire_ID = original_fire_id[e];
								int dynamic_POD_i = i + 1;
								int dynamic_POD_j = j + 1;
								String var_name = "Y_" + fire_ID + "_" + dynamic_POD_i + "_" + dynamic_POD_j;
								Information_Variable var_info = new Information_Variable(var_name);
								var_info_list.add(var_info);
								objlist.add((double) 0);
								vnamelist.add(var_name);
								vlblist.add((double) 0);
								vublist.add((double) 1);
								vtlist.add(IloNumVarType.Bool);
								Y[e][i][j] = nvars;
								nvars++;
							}
						}
					}
				}
				
				int[][][] B = null;	// B(e,ie,je) with e is the FireID, i and j are the dynamic POD
				B = new int[number_of_fires][][];
				for (int e = 0; e < number_of_fires; e++) {
					B[e] = new int[number_of_PODS[e]][];
					for (int i = 0; i < number_of_PODS[e]; i++) {
						B[e][i] = new int[number_of_PODS[e]];
						for (int j = 0; j < number_of_PODS[e]; j++) {
							if (adjacent_PODS[e][i].contains(j)) {
								int fire_ID = original_fire_id[e];
								int dynamic_POD_i = i + 1;
								int dynamic_POD_j = j + 1;
								String var_name = "B_" + fire_ID + "_" + dynamic_POD_i + "_" + dynamic_POD_j;
								Information_Variable var_info = new Information_Variable(var_name);
								var_info_list.add(var_info);
								objlist.add((double) 0);
								vnamelist.add(var_name);
								vlblist.add((double) 0);
								vublist.add((double) 1);
								vtlist.add(IloNumVarType.Bool);
								B[e][i][j] = nvars;
								nvars++;
							}
						}
					}
				}
				
				// Convert lists to 1-D arrays
				double[] objvals = Stream.of(objlist.toArray(new Double[objlist.size()])).mapToDouble(Double::doubleValue).toArray();
				objlist = null;			// Clear the lists to save memory
				String[] vname = vnamelist.toArray(new String[nvars]);
				vnamelist = null;		// Clear the lists to save memory
				double[] vlb = Stream.of(vlblist.toArray(new Double[vlblist.size()])).mapToDouble(Double::doubleValue).toArray();
				vlblist = null;			// Clear the lists to save memory
				double[] vub = Stream.of(vublist.toArray(new Double[vublist.size()])).mapToDouble(Double::doubleValue).toArray();
				vublist = null;			// Clear the lists to save memory
				IloNumVarType[] vtype = vtlist.toArray(new IloNumVarType[vtlist.size()]);						
				vtlist = null;		// Clear the lists to save memory
				Information_Variable[] var_info_array = new Information_Variable[nvars];		// This array stores variable information
				for (int i = 0; i < nvars; i++) {
					var_info_array[i] = var_info_list.get(i);
				}	
				var_info_list = null;	// Clear the lists to save memory
				
				
				
				
				// CREATE CONSTRAINTS-------------------------------------------------
				// CREATE CONSTRAINTS-------------------------------------------------
				// CREATE CONSTRAINTS-------------------------------------------------
				// NOTE: Constraint bounds are optimized for better performance
				// Constraints 2------------------------------------------------------
				List<List<Integer>> c2_indexlist = new ArrayList<List<Integer>>();	
				List<List<Double>> c2_valuelist = new ArrayList<List<Double>>();
				List<Double> c2_lblist = new ArrayList<Double>();	
				List<Double> c2_ublist = new ArrayList<Double>();
				int c2_num = 0;
				
				// 2a
				for (int b = 0; b < number_of_fuelbreaks; b++) {
					// Add constraint
					c2_indexlist.add(new ArrayList<Integer>());
					c2_valuelist.add(new ArrayList<Double>());
					
					for (int k = 0; k < number_of_management_options; k++) {
						// Add sigma D[b][k]
						c2_indexlist.get(c2_num).add(D[b][k]);
						c2_valuelist.get(c2_num).add((double) 1);
					}
					
					// add bounds
					c2_lblist.add((double) 0);	
					c2_ublist.add((double) 1);	
					c2_num++;
				}
				
				
				// 2b
				for (int b = 0; b < number_of_fuelbreaks; b++) {
					// Add constraint
					c2_indexlist.add(new ArrayList<Integer>());
					c2_valuelist.add(new ArrayList<Double>());
					
					// Add Q[b]
					c2_indexlist.get(c2_num).add(Q[b]);
					c2_valuelist.get(c2_num).add((double) 1);
					
					for (int k = 0; k < number_of_management_options; k++) {
						// Add - sigma q[b][k] * D[b][k]
						c2_indexlist.get(c2_num).add(D[b][k]);
						c2_valuelist.get(c2_num).add(-q[b][k]);
					}
					
					// add bounds
					c2_lblist.add((double) 0);	
					c2_ublist.add((double) 0);	
					c2_num++;
				}
				
				// 2c
				for (int e = 0; e < number_of_fires; e++) {
					for (int i = 0; i < number_of_PODS[e] - 1; i++) {
						for (int j = i + 1; j < number_of_PODS[e]; j++) {
							for (int b : b_list[e][i][j]) {
								// Add constraint
								c2_indexlist.add(new ArrayList<Integer>());
								c2_valuelist.add(new ArrayList<Double>());

								// Add fl[e][i][j] * Y[e][i][j]
								c2_indexlist.get(c2_num).add(Y[e][i][j]);
								c2_valuelist.get(c2_num).add(fl_list[e][i][j].get(b_list[e][i][j].indexOf(b)));		// index of break_id in the b_list is associated with index of that break flame length in the fl_list
								
								// Add -Q[b]
								c2_indexlist.get(c2_num).add(Q[b]);
								c2_valuelist.get(c2_num).add((double) -1);

								// add bounds
								c2_lblist.add((double) -Double.MAX_VALUE);	// Lower bound = negative flame length
								c2_ublist.add((double) 0);		// Upper bound = 0
								c2_num++;
							}
						}
					}
				}
				
				double[] c2_lb = Stream.of(c2_lblist.toArray(new Double[c2_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
				double[] c2_ub = Stream.of(c2_ublist.toArray(new Double[c2_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
				int[][] c2_index = new int[c2_num][];
				double[][] c2_value = new double[c2_num][];

				for (int i = 0; i < c2_num; i++) {
					c2_index[i] = new int[c2_indexlist.get(i).size()];
					c2_value[i] = new double[c2_indexlist.get(i).size()];
					for (int j = 0; j < c2_indexlist.get(i).size(); j++) {
						c2_index[i][j] = c2_indexlist.get(i).get(j);
						c2_value[i][j] = c2_valuelist.get(i).get(j);			
					}
				}	
				
				// Clear lists to save memory
				c2_indexlist = null;	
				c2_valuelist = null;
				c2_lblist = null;	
				c2_ublist = null;
				System.out.println("Total constraints as in model formulation eq. (2):   " + c2_num + "             " + dateFormat.format(new Date()));
				
				
				// Constraints 3------------------------------------------------------
				List<List<Integer>> c3_indexlist = new ArrayList<List<Integer>>();	
				List<List<Double>> c3_valuelist = new ArrayList<List<Double>>();
				List<Double> c3_lblist = new ArrayList<Double>();	
				List<Double> c3_ublist = new ArrayList<Double>();
				int c3_num = 0;
				
				for (int e = 0; e < number_of_fires; e++) {
					for (int i = 0; i < number_of_PODS[e]; i++) {
						if (i == ignition_POD[e]) {
							// Add constraint
							c3_indexlist.add(new ArrayList<Integer>());
							c3_valuelist.add(new ArrayList<Double>());

							// Add X[e][i]		--> POD i is the ignition location
							c3_indexlist.get(c3_num).add(X[e][i]);
							c3_valuelist.get(c3_num).add((double) 1);

							// add bounds
							c3_lblist.add((double) 1);	// Lower bound = 1
							c3_ublist.add((double) 1);	// Upper bound = 1
							c3_num++;
						}
					}
				}
				
				double[] c3_lb = Stream.of(c3_lblist.toArray(new Double[c3_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
				double[] c3_ub = Stream.of(c3_ublist.toArray(new Double[c3_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
				int[][] c3_index = new int[c3_num][];
				double[][] c3_value = new double[c3_num][];

				for (int i = 0; i < c3_num; i++) {
					c3_index[i] = new int[c3_indexlist.get(i).size()];
					c3_value[i] = new double[c3_indexlist.get(i).size()];
					for (int j = 0; j < c3_indexlist.get(i).size(); j++) {
						c3_index[i][j] = c3_indexlist.get(i).get(j);
						c3_value[i][j] = c3_valuelist.get(i).get(j);			
					}
				}	
				
				// Clear lists to save memory
				c3_indexlist = null;	
				c3_valuelist = null;
				c3_lblist = null;	
				c3_ublist = null;
				System.out.println("Total constraints as in model formulation eq. (3):   " + c3_num + "             " + dateFormat.format(new Date()));
				
									
				// Constraints 5------------------------------------------------------		
				List<List<Integer>> c5_indexlist = new ArrayList<List<Integer>>();	
				List<List<Double>> c5_valuelist = new ArrayList<List<Double>>();
				List<Double> c5_lblist = new ArrayList<Double>();	
				List<Double> c5_ublist = new ArrayList<Double>();
				int c5_num = 0;
				
				for (int e = 0; e < number_of_fires; e++) {
					for (int i = 0; i < number_of_PODS[e]; i++) {
						for (int j : adjacent_PODS[e][i]) {
							if (j != ignition_POD[e]) {	// if this is not the ignition POD
								// Add constraint
								c5_indexlist.add(new ArrayList<Integer>());
								c5_valuelist.add(new ArrayList<Double>());
								
								// Add B[e][i][j]
								c5_indexlist.get(c5_num).add(B[e][i][j]);
								c5_valuelist.get(c5_num).add((double) 1);
								
								// Add -X[e][i]
								c5_indexlist.get(c5_num).add(X[e][i]);
								c5_valuelist.get(c5_num).add((double) -1);
								
								// add bounds
								c5_lblist.add((double) -1);		// Lower bound = -1 will make this equation not fail
								c5_ublist.add((double) 0);		// Upper bound = 0
								c5_num++;
							} else {	// if this is the ignition POD then set all B = 0 to avoid spreading back to ignition POD
								// Add constraint
								c5_indexlist.add(new ArrayList<Integer>());
								c5_valuelist.add(new ArrayList<Double>());
								
								// Add B[e][i][j]
								c5_indexlist.get(c5_num).add(B[e][i][j]);
								c5_valuelist.get(c5_num).add((double) 1);
								
								// add bounds
								c5_lblist.add((double) 0);
								c5_ublist.add((double) 0);		// Upper bound = 0
								c5_num++;
							}
						}
					}
				}
				
				double[] c5_lb = Stream.of(c5_lblist.toArray(new Double[c5_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
				double[] c5_ub = Stream.of(c5_ublist.toArray(new Double[c5_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
				int[][] c5_index = new int[c5_num][];
				double[][] c5_value = new double[c5_num][];

				for (int i = 0; i < c5_num; i++) {
					c5_index[i] = new int[c5_indexlist.get(i).size()];
					c5_value[i] = new double[c5_indexlist.get(i).size()];
					for (int j = 0; j < c5_indexlist.get(i).size(); j++) {
						c5_index[i][j] = c5_indexlist.get(i).get(j);
						c5_value[i][j] = c5_valuelist.get(i).get(j);			
					}
				}	
				
				// Clear lists to save memory
				c5_indexlist = null;	
				c5_valuelist = null;
				c5_lblist = null;	
				c5_ublist = null;
				System.out.println("Total constraints as in model formulation eq. (5):   " + c5_num + "             " + dateFormat.format(new Date()));
					
				
				// Constraints 6------------------------------------------------------
				List<List<Integer>> c6_indexlist = new ArrayList<List<Integer>>();	
				List<List<Double>> c6_valuelist = new ArrayList<List<Double>>();
				List<Double> c6_lblist = new ArrayList<Double>();	
				List<Double> c6_ublist = new ArrayList<Double>();
				int c6_num = 0;
				
				// 6a
				for (int e = 0; e < number_of_fires; e++) {
					for (int i = 0; i < number_of_PODS[e] - 1; i++) {
						for (int j = i + 1; j < number_of_PODS[e]; j++) {
							if (adjacent_PODS[e][i].contains(j)) {
								// Add constraint
								c6_indexlist.add(new ArrayList<Integer>());
								c6_valuelist.add(new ArrayList<Double>());
								
								// Add Y[e][i][j]
								c6_indexlist.get(c6_num).add(Y[e][i][j]);
								c6_valuelist.get(c6_num).add((double) 1);
								
								// Add -X[e][i]
								c6_indexlist.get(c6_num).add(X[e][i]);
								c6_valuelist.get(c6_num).add((double) -1);
								
								// Add +X[e][j]
								c6_indexlist.get(c6_num).add(X[e][j]);
								c6_valuelist.get(c6_num).add((double) 1);
								
								// add bounds
								c6_lblist.add((double) 0);			// Lower bound = 0
								c6_ublist.add((double) 2);			// Upper bound = 2 (bound is modified to optimize better)
								c6_num++;
							}
						}
					}
				}
				
				// 6b
				for (int e = 0; e < number_of_fires; e++) {
					for (int i = 0; i < number_of_PODS[e] - 1; i++) {
						for (int j = i + 1; j < number_of_PODS[e]; j++) {
							if (adjacent_PODS[e][i].contains(j)) {
								// Add constraint
								c6_indexlist.add(new ArrayList<Integer>());
								c6_valuelist.add(new ArrayList<Double>());
								
								// Add Y[e][i][j]
								c6_indexlist.get(c6_num).add(Y[e][i][j]);
								c6_valuelist.get(c6_num).add((double) 1);
								
								// Add +X[e][i]
								c6_indexlist.get(c6_num).add(X[e][i]);
								c6_valuelist.get(c6_num).add((double) 1);
								
								// Add -X[e][j]
								c6_indexlist.get(c6_num).add(X[e][j]);
								c6_valuelist.get(c6_num).add((double) -1);
								
								// add bounds
								c6_lblist.add((double) 0);	// Lower bound = 0
								c6_ublist.add((double) 2);	// Upper bound = 2 (bound is modified to optimize better)
								c6_num++;
							}
						}
					}
				}

				double[] c6_lb = Stream.of(c6_lblist.toArray(new Double[c6_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
				double[] c6_ub = Stream.of(c6_ublist.toArray(new Double[c6_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
				int[][] c6_index = new int[c6_num][];
				double[][] c6_value = new double[c6_num][];

				for (int i = 0; i < c6_num; i++) {
					c6_index[i] = new int[c6_indexlist.get(i).size()];
					c6_value[i] = new double[c6_indexlist.get(i).size()];
					for (int j = 0; j < c6_indexlist.get(i).size(); j++) {
						c6_index[i][j] = c6_indexlist.get(i).get(j);
						c6_value[i][j] = c6_valuelist.get(i).get(j);			
					}
				}	
				
				// Clear lists to save memory
				c6_indexlist = null;	
				c6_valuelist = null;
				c6_lblist = null;	
				c6_ublist = null;
				System.out.println("Total constraints as in model formulation eq. (6):   " + c6_num + "             " + dateFormat.format(new Date()));
				
				
				// Constraints 7------------------------------------------------------		
				List<List<Integer>> c7_indexlist = new ArrayList<List<Integer>>();	
				List<List<Double>> c7_valuelist = new ArrayList<List<Double>>();
				List<Double> c7_lblist = new ArrayList<Double>();	
				List<Double> c7_ublist = new ArrayList<Double>();
				int c7_num = 0;
				
				for (int e = 0; e < number_of_fires; e++) {
					for (int i = 0; i < number_of_PODS[e]; i++) {
						for (int j : adjacent_PODS[e][i]) {
							// 7a
							if (i < j) {
								// Add constraint
								c7_indexlist.add(new ArrayList<Integer>());
								c7_valuelist.add(new ArrayList<Double>());
								
								// Add B[e][i][j]
								c7_indexlist.get(c7_num).add(B[e][i][j]);
								c7_valuelist.get(c7_num).add((double) 1);
								
								// Add Y[e][i][j]
								c7_indexlist.get(c7_num).add(Y[e][i][j]);
								c7_valuelist.get(c7_num).add((double) 1);
								
								// add bounds
								c7_lblist.add((double) 0);		// Lower bound = 0
								c7_ublist.add((double) 1);		// Upper bound = 1
								c7_num++;
							}
							
							// 7b
							if (i > j) {
								// Add constraint
								c7_indexlist.add(new ArrayList<Integer>());
								c7_valuelist.add(new ArrayList<Double>());
								
								// Add B[e][i][j]
								c7_indexlist.get(c7_num).add(B[e][i][j]);
								c7_valuelist.get(c7_num).add((double) 1);
								
								// Add Y[e][j][i]
								c7_indexlist.get(c7_num).add(Y[e][j][i]);
								c7_valuelist.get(c7_num).add((double) 1);
								
								// add bounds
								c7_lblist.add((double) 0);		// Lower bound = 0
								c7_ublist.add((double) 1);		// Upper bound = 1
								c7_num++;
							}
						}
					}
				}
				
				double[] c7_lb = Stream.of(c7_lblist.toArray(new Double[c7_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
				double[] c7_ub = Stream.of(c7_ublist.toArray(new Double[c7_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
				int[][] c7_index = new int[c7_num][];
				double[][] c7_value = new double[c7_num][];

				for (int i = 0; i < c7_num; i++) {
					c7_index[i] = new int[c7_indexlist.get(i).size()];
					c7_value[i] = new double[c7_indexlist.get(i).size()];
					for (int j = 0; j < c7_indexlist.get(i).size(); j++) {
						c7_index[i][j] = c7_indexlist.get(i).get(j);
						c7_value[i][j] = c7_valuelist.get(i).get(j);			
					}
				}	
				
				// Clear lists to save memory
				c7_indexlist = null;	
				c7_valuelist = null;
				c7_lblist = null;	
				c7_ublist = null;
				System.out.println("Total constraints as in model formulation eq. (7):   " + c7_num + "             " + dateFormat.format(new Date()));
				
				
				// Constraints 10------------------------------------------------------		
				List<List<Integer>> c10_indexlist = new ArrayList<List<Integer>>();	
				List<List<Double>> c10_valuelist = new ArrayList<List<Double>>();
				List<Double> c10_lblist = new ArrayList<Double>();	
				List<Double> c10_ublist = new ArrayList<Double>();
				int c10_num = 0;
				
				// 10a
				for (int b = 0; b < number_of_fuelbreaks; b++) {
					// Add constraint
					c10_indexlist.add(new ArrayList<Integer>());
					c10_valuelist.add(new ArrayList<Double>());
					
					// Add A[b]
					c10_indexlist.get(c10_num).add(A[b]);
					c10_valuelist.get(c10_num).add((double) 1);
					
					for (int k = 0; k < number_of_management_options; k++) {
						// Add - sigma a[b][k] * D[b][k]
						c10_indexlist.get(c10_num).add(D[b][k]);
						c10_valuelist.get(c10_num).add(-break_area[b][k]);
					}
					
					// add bounds
					c10_lblist.add((double) 0);	
					c10_ublist.add((double) 0);	
					c10_num++;
				}
				
				// 10b
				// Add constraint
				c10_indexlist.add(new ArrayList<Integer>());
				c10_valuelist.add(new ArrayList<Double>());
				
				for (int b = 0; b < number_of_fuelbreaks; b++) {
					// Add Sigma A[b]
					c10_indexlist.get(c10_num).add(A[b]);
					c10_valuelist.get(c10_num).add((double) 1);
				}
				
				// add bounds
				c10_lblist.add((double) 0);			// Lower bound = 0
				c10_ublist.add(budget);				// Upper bound = budget
				c10_num++;
				
				double[] c10_lb = Stream.of(c10_lblist.toArray(new Double[c10_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
				double[] c10_ub = Stream.of(c10_ublist.toArray(new Double[c10_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
				int[][] c10_index = new int[c10_num][];
				double[][] c10_value = new double[c10_num][];

				for (int i = 0; i < c10_num; i++) {
					c10_index[i] = new int[c10_indexlist.get(i).size()];
					c10_value[i] = new double[c10_indexlist.get(i).size()];
					for (int j = 0; j < c10_indexlist.get(i).size(); j++) {
						c10_index[i][j] = c10_indexlist.get(i).get(j);
						c10_value[i][j] = c10_valuelist.get(i).get(j);			
					}
				}	
				
				// Clear lists to save memory
				c10_indexlist = null;	
				c10_valuelist = null;
				c10_lblist = null;	
				c10_ublist = null;
				System.out.println("Total constraints as in model formulation eq. (10):   " + c10_num + "             " + dateFormat.format(new Date()));
				time_end = System.currentTimeMillis();		// measure time after reading
				time_reading = (double) (time_end - time_start) / 1000;
				
				
				
				
				
				// SOLVE --------------------------------------------------------------
				// SOLVE --------------------------------------------------------------
				// SOLVE --------------------------------------------------------------
				try {
					// Add the CPLEX native library path dynamically at run time
//						LibraryHandle.setLibraryPath(FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
//						LibraryHandle.addLibraryPath(FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
//						System.out.println("Successfully loaded CPLEX .dll files from " + FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
					
					System.out.println("Prism found the below java library paths:");
					String property = System.getProperty("java.library.path");
					StringTokenizer parser = new StringTokenizer(property, ";");
					while (parser.hasMoreTokens()) {
						System.out.println("           - " + parser.nextToken());
					}
					
					IloCplex cplex = new IloCplex();
					IloLPMatrix lp = cplex.addLPMatrix();
					IloNumVar[] var = cplex.numVarArray(cplex.columnArray(lp, nvars), vlb, vub, vtype, vname);
					vlb = null; vub = null; vtype = null; // vname = null;		// Clear arrays to save memory
					
					// Add constraints
					lp.addRows(c2_lb, c2_ub, c2_index, c2_value); 		// Constraints 2
					lp.addRows(c3_lb, c3_ub, c3_index, c3_value); 		// Constraints 3
//					lp.addRows(c4_lb, c4_ub, c4_index, c4_value); 		// Constraints 4
					lp.addRows(c5_lb, c5_ub, c5_index, c5_value); 		// Constraints 5
					lp.addRows(c6_lb, c6_ub, c6_index, c6_value);		// Constraints 6
					lp.addRows(c7_lb, c7_ub, c7_index, c7_value); 		// Constraints 7
//					lp.addRows(c8_lb, c8_ub, c8_index, c8_value);		// Constraints 8
//					lp.addRows(c9_lb, c9_ub, c9_index, c9_value); 		// Constraints 9
					lp.addRows(c10_lb, c10_ub, c10_index, c10_value); 	// Constraints 10
					
					// Clear arrays to save memory
					c2_lb = null;  c2_ub = null;  c2_index = null;  c2_value = null;
					c3_lb = null;  c3_ub = null;  c3_index = null;  c3_value = null;
					c5_lb = null;  c5_ub = null;  c5_index = null;  c5_value = null;
					c6_lb = null;  c6_ub = null;  c6_index = null;  c6_value = null;
					c7_lb = null;  c7_ub = null;  c7_index = null;  c7_value = null;
//					c8_lb = null;  c8_ub = null;  c8_index = null;  c8_value = null;
//					c9_lb = null;  c9_ub = null;  c9_index = null;  c9_value = null;
					c10_lb = null;  c10_ub = null;  c10_index = null;  c10_value = null;
					
					// Set constraints set name: Notice THIS WILL EXTREMELY SLOW THE SOLVING PROCESS (recommend for debugging only)
					int indexOfC2 = c2_num;
					int indexOfC3 = indexOfC2 + c3_num;
//					int indexOfC4 = indexOfC3 + c4_num;
					int indexOfC5 = indexOfC3 + c5_num;
					int indexOfC6 = indexOfC5 + c6_num;
					int indexOfC7 = indexOfC6 + c7_num;
//					int indexOfC8 = indexOfC7 + c8_num;
//					int indexOfC9 = indexOfC8 + c9_num;
//					int indexOfC10 = indexOfC9 + c10_num;	// Note: lp.getRanges().length = indexOfC10
					int indexOfC10 = indexOfC7 + c10_num;	// Note: lp.getRanges().length = indexOfC10
					for (int i = 0; i < lp.getRanges().length; i++) {	
						if (0 <= i && i < indexOfC2) lp.getRanges() [i].setName("S.2");
						if (indexOfC2<=i && i<indexOfC3) lp.getRanges() [i].setName("S.3" + i);
//						if (indexOfC3<=i && i<indexOfC4) lp.getRanges() [i].setName("S.4" + i);
						if (indexOfC3<=i && i<indexOfC5) lp.getRanges() [i].setName("S.5" + i);
						if (indexOfC5<=i && i<indexOfC6) lp.getRanges() [i].setName("S.6" + i);
						if (indexOfC6<=i && i<indexOfC7) lp.getRanges() [i].setName("S.7" + i);
//						if (indexOfC7<=i && i<indexOfC8) lp.getRanges() [i].setName("S.8" + i);
//						if (indexOfC8<=i && i<indexOfC9) lp.getRanges() [i].setName("S.9" + i);
//						if (indexOfC9<=i && i<indexOfC10) lp.getRanges() [i].setName("S.10" + i);
						if (indexOfC7<=i && i<indexOfC10) lp.getRanges() [i].setName("S.10" + i);
					}
					
					cplex.addMinimize(cplex.scalProd(var, objvals));
					objvals = null;		// Clear arrays to save memory
//						cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Auto); // Auto choose optimization method
					cplex.setParam(IloCplex.Param.MIP.Strategy.Search, IloCplex.MIPSearch.Traditional);		// MIP method
//					cplex.setParam(IloCplex.DoubleParam.EpAGap, optimality_gap);	// absolute MIP gap tolerance: https://www.ibm.com/docs/en/icos/20.1.0?topic=parameters-absolute-mip-gap-tolerance
					if (optimality_gap > 0) cplex.setParam(IloCplex.DoubleParam.EpGap, optimality_gap); 	// relative MIP gap tolerance: https://www.ibm.com/docs/en/icos/20.1.0?topic=parameters-relative-mip-gap-tolerance
//						int solvingTimeLimit = 30 * 60; //Get time Limit in minute * 60 = seconds
//						cplex.setParam(IloCplex.DoubleParam.TimeLimit, solvingTimeLimit); // Set Time limit
//						cplex.setParam(IloCplex.Param.MIP.Tolerances.Integrality, 0); 	// Set integrality tolerance: https://www.ibm.com/docs/en/icos/20.1.0?topic=parameters-integrality-tolerance;    https://www.ibm.com/support/pages/why-does-binary-or-integer-variable-take-noninteger-value-solution
//						cplex.setParam(IloCplex.BooleanParam.PreInd, false);			// page 40: sets the Boolean parameter PreInd to false, instructing CPLEX not to apply presolve before solving the problem.
//						cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);	// turn off presolve to prevent it from completely solving the model before entering the actual LP optimizer (same as above ???)
					
					time_start = System.currentTimeMillis();		// measure time before solving
					if (cplex.solve()) {
						if (export_problem_file) cplex.exportModel(problem_file.getAbsolutePath());
						if (export_solution_file) cplex.writeSolution(solution_file.getAbsolutePath());
						double[] value = cplex.getValues(lp);
						// double[] reduceCost = cplex.getReducedCosts(lp);
						// double[] dual = cplex.getDuals(lp);
						// double[] slack = cplex.getSlacks(lp);
						double objective_value = cplex.getObjValue();
						Status cplex_status = cplex.getStatus();
						int cplex_algorithm = cplex.getAlgorithm();
						long cplex_iteration = cplex.getNiterations64();
						int cplex_total_variables = cplex.getNcols();
						int cplex_total_constraints = cplex.getNrows();
						double solution_gap = cplex.getMIPRelativeGap();
						time_end = System.currentTimeMillis();		// measure time after solving
						time_solving = (double) (time_end - time_start) / 1000;
						
						// WRITE SOLUTION --------------------------------------------------------------
						// WRITE SOLUTION --------------------------------------------------------------
						// WRITE SOLUTION --------------------------------------------------------------
						System.out.println("Objective function value = " + objective_value);
						
						// output_01_all_variables
						output_solution_summary_file.delete();
						try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(output_solution_summary_file))) {
							double length_of_fuelbreaks = Arrays.stream(break_length).sum();
							int num_breaks_treat = 0;
							int[] num_breaks_treat_k = new int[number_of_management_options];
							double length_breaks_treat = 0;
							double[] length_breaks_treat_k = new double[number_of_management_options];
							double area_breaks_treat = 0;
							double[] area_breaks_treat_k = new double[number_of_management_options];
							
							for (int i = 0; i < value.length; i++) {
								if (vname[i].startsWith("D")) {
									int break_id = var_info_array[i].get_break_ID();
									int management_option = var_info_array[i].get_magegement_option();
									if (Math.round(value[i]) == 1) {	// if not round then solution such as 0.999999 will not be counted.
										num_breaks_treat_k[management_option - 1]++;	// because k start from 0 in the model
										num_breaks_treat++;
										
										length_breaks_treat_k[management_option - 1] = length_breaks_treat_k[management_option - 1] + break_length[break_id - 1];
										length_breaks_treat = length_breaks_treat + break_length[break_id - 1];
										
										area_breaks_treat_k[management_option - 1] = area_breaks_treat_k[management_option - 1] + break_area[break_id - 1][management_option - 1];
										area_breaks_treat = area_breaks_treat + break_area[break_id - 1][management_option - 1];
									}
								}
							}
							int num_breaks_no_treat = number_of_fuelbreaks - num_breaks_treat;
							double length_breaks_no_treat = length_of_fuelbreaks - length_breaks_treat;
							
							fileOut.write("budget" + "\t" + budget);
							fileOut.newLine(); fileOut.write("test_case_description" + "\t" + test_case_description);
							fileOut.newLine(); fileOut.write("num_variables" + "\t" + cplex_total_variables); 
							fileOut.newLine(); fileOut.write("num_constraints" + "\t" + cplex_total_constraints); 
							fileOut.newLine(); fileOut.write("optimality_gap" + "\t" + optimality_gap);
							fileOut.newLine(); fileOut.write("cplex_algorithm" + "\t" + cplex_algorithm);
							fileOut.newLine(); fileOut.write("cplex_iteration" + "\t" + cplex_iteration);
							fileOut.newLine(); fileOut.write("cplex_status" + "\t" + cplex_status);
							fileOut.newLine(); fileOut.write("solution_gap" + "\t" + solution_gap);
							fileOut.newLine(); fileOut.write("solution_time" + "\t" + time_solving);
							fileOut.newLine(); fileOut.write("solution_objective_value" + "\t" + objective_value);
							fileOut.newLine(); fileOut.write("num_breaks_treat" + "\t" + num_breaks_treat);
							fileOut.newLine(); fileOut.write("num_breaks_no_treat" + "\t" + num_breaks_no_treat);
							for (int k = 1; k < number_of_management_options + 1; k++ ) {
								fileOut.newLine();
								fileOut.write("num_breaks_treat_k" + k + "\t" + num_breaks_treat_k[k - 1]);	// because k start from 0 in the model
							}
							fileOut.newLine(); fileOut.write("length_breaks_treat" + "\t" + length_breaks_treat);
							fileOut.newLine(); fileOut.write("length_breaks_no_treat" + "\t" + length_breaks_no_treat);
							for (int k = 1; k < number_of_management_options + 1; k++ ) {
								fileOut.newLine();
								fileOut.write("length_breaks_treat_k" + k + "\t" + length_breaks_treat_k[k - 1]);	// because k start from 0 in the model
							}
							fileOut.newLine(); fileOut.write("area_breaks_treat" + "\t" + area_breaks_treat);
							fileOut.newLine(); fileOut.write("area_breaks_no_treat" + "\t" + "NA");
							for (int k = 1; k < number_of_management_options + 1; k++ ) {
								fileOut.newLine();
								fileOut.write("area_breaks_treat_k" + k + "\t" + area_breaks_treat_k[k - 1]);	// because k start from 0 in the model
							}
							
							fileOut.close();
						} catch (IOException e) {
							System.err.println("FileWriter(output_solution_summary_file error - "	+ e.getClass().getName() + ": " + e.getMessage());
						}
						output_solution_summary_file.createNewFile();	
						
						
						// output_2_fuelbreaks_decisions
						output_fuelbreaks_decisions_file.delete();
						try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(output_fuelbreaks_decisions_file))) {
							int[] selected_solution = new int[number_of_fuelbreaks];
							for (int b = 0; b < number_of_fuelbreaks; b++) {
								selected_solution[b] = 0;	// no maintenance to align with GIS file (k=0 is not 100 FT wide anymore)
							}
							
							for (int i = 0; i < value.length; i++) {
								if (vname[i].startsWith("D")) {
									int break_id = var_info_array[i].get_break_ID();
									int management_option = var_info_array[i].get_magegement_option();
									if (Math.round(value[i]) == 1) {	// if not round then solution such as 0.999999 will not be counted.
										selected_solution[break_id - 1] = management_option;						
									}
								}
							}
							
							fileOut.write("break_id" + "\t" + "selected_k" + "\t" + "k_0" + "\t" + "k_1" + "\t" + "k_2" + "\t" + "k_3" + "\t" + "k_4" + "\t"); 
							for (int b = 0; b < number_of_fuelbreaks; b++) {
								int break_id = b + 1;	// to align with GIS file (break will not start from 0, but 1)
								fileOut.newLine();
								fileOut.write(break_id + "\t" + selected_solution[b]); 
								for (int k = 0; k < number_of_management_options + 1; k++) {	// loop from 0 to 4 associated with no management (k_0), 100 FT (K_1), 200 FT, 300 FT, 400 FT, to align with GIS file
									if (selected_solution[b] == k) {
										fileOut.write("\t" + 1); 
									} else {
										fileOut.write("\t" + 0); 
									}
								}
							}
							
							fileOut.close();
						} catch (IOException e) {
							System.err.println("FileWriter(output_2_fuelbreaks_decisions error - "	+ e.getClass().getName() + ": " + e.getMessage());
						}
						output_fuelbreaks_decisions_file.createNewFile();			
						
						
						// output_03_all_variables
						output_all_variables_file.delete();
						try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(output_all_variables_file))) {
							String file_header = String.join("\t", "var_id", "var_name", "var_value");
							fileOut.write(file_header);
							for (int i = 0; i < value.length; i++) {
								fileOut.newLine();
								fileOut.write(i + "\t" + vname[i] + "\t" + value[i]);
							}
							fileOut.close();
						} catch (IOException e) {
							System.err.println("FileWriter(output_variables_file) error - "	+ e.getClass().getName() + ": " + e.getMessage());
						}
						output_all_variables_file.createNewFile();		
						
						
						// output_04_nonzero_all_variables
						output_nonzero_variables_file.delete();
						try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(output_nonzero_variables_file))) {
							String file_header = String.join("\t", "var_id", "var_name", "var_value");
							fileOut.write(file_header);
							for (int i = 0; i < value.length; i++) {
								if (Math.round(value[i]) != 0) {	// only write variable that is not zero, avoid print out very very small number such as 0.0000000000012
									fileOut.newLine();
									fileOut.write(i + "\t" + vname[i] + "\t" + value[i]);
								}
							}
							fileOut.close();
						} catch (IOException e) {
							System.err.println("FileWriter(output_nonzero_variables_file) error - "	+ e.getClass().getName() + ": " + e.getMessage());
						}
						output_nonzero_variables_file.createNewFile();	
					}
					cplex.end();
				} catch (Exception e) {
					System.err.println("Panel Solve Runs - cplexLib.addLibraryPath error - " + e.getClass().getName() + ": " + e.getMessage());
				}
			}
		});
	}
	
	public static Main get_main() {
		return main;
	}
	
	public String get_workingLocation() {
		// Get working location of spectrumLite
		String workingLocation;
		// Get working location of the IDE project, or runnable jar file
		final File jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
		workingLocation = jarFile.getParentFile().toString();
		// Make the working location with correct name
		try {
			// to handle name with space (%20)
			workingLocation = URLDecoder.decode(workingLocation, "utf-8");
			workingLocation = new File(workingLocation).getPath();
		} catch (UnsupportedEncodingException e) {
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}
		return workingLocation;
	}
}
