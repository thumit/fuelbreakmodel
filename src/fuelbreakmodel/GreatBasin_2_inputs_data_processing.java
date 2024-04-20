package fuelbreakmodel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GreatBasin_2_inputs_data_processing {
	private int number_of_fires;	// number of fires
	private int[] original_fire_id; // the original fire_id: which is "fire_id" in the input_01_file of the example, or "FIRE_NUMBE" in the great basin attribute table
	private int[] number_of_PODS; 	// number of dynamic PODs for each fire
	private int[] n; 				// number of dynamic PODs for each fire minus one
	private int[] ignition_POD; 	// the ignition POD of each fire
												
	private double[][] ENVC;					// w(e,ie) in the objective function, with e is the FireID, i is the dynamic POD			
	private List<Integer>[][] adjacent_PODS;	// Adjacent PODs
					
	private int number_of_fuelbreaks;
	private double[] q0; 	// the current capacity of a fuel break
	private double[] d1; 	// parameter for D1 variable
	private double[] d2; 	// parameter for D2 variable
	private double[] d3; 	// parameter for D3 variable										
	
	private List<Integer>[][][] b_list;	// b_list stores all the breaks within the shared boundary of 2 adjacent polygons i and j of fire e										
	private List<Double>[][][] fl_list;	// fl_list stores flame lengths across all the break segments within the shared boundary of 2 adjacent polygons i and j of fire e		

	public GreatBasin_2_inputs_data_processing(File input_1_file, File input_2_file) {
		try {
			// Read input_1 --------------------------------------------------------------------------------------------
			// Note that the input has been sorted by increasing values of "FIRE_NUMBE" and "poly_id"
			List<String> list = Files.readAllLines(Paths.get(input_1_file.getAbsolutePath()), StandardCharsets.UTF_8);
			list.remove(0);	// Remove the first row (header)
			String[] a = list.toArray(new String[list.size()]);
			int total_rows = a.length;
			int total_columns = a[0].split("\t").length;				
			String[][] data = new String[total_rows][total_columns];
		
			// read all values from all rows and columns
			for (int i = 0; i < total_rows; i++) {
				String[] rowValue = a[i].split("\t");
				for (int j = 0; j < total_columns; j++) {
					data[i][j] = rowValue[j];
				}
			}
			
			// List<List<String>> fire_infor = new ArrayList<List<String>>();
			List<Integer> original_fire_id_list = new ArrayList<Integer>();	
			List<Integer> num_of_PODS_list = new ArrayList<Integer>();	

			number_of_fires = 0;
			for (int i = 0; i < total_rows; i++) {
				int origin_id = Integer.parseInt(data[i][0]);				// 'FIRENUMBE' column
				int num_pods = Integer.parseInt(data[i][9]);				// 'num_polys' column
				if (!original_fire_id_list.contains(origin_id)) {
					original_fire_id_list.add(origin_id);
					num_of_PODS_list.add(num_pods);
					number_of_fires++;
				}
			}
			original_fire_id = Stream.of(original_fire_id_list.toArray(new Integer[original_fire_id_list.size()])).mapToInt(Integer::intValue).toArray();	// convert list to array
			number_of_PODS = Stream.of(num_of_PODS_list.toArray(new Integer[num_of_PODS_list.size()])).mapToInt(Integer::intValue).toArray();	// convert list to array		
			n = new int[number_of_fires]; 				// number of dynamic PODs for each fire minus one
			ignition_POD = new int[number_of_fires]; 	// the ignition POD of each fire
			for (int e = 0; e < number_of_fires; e++) {
				n[e] = number_of_PODS[e] - 1;
				ignition_POD[e] = 1 - 1;	// poly_id = 1 is always the Ignition pod in the attribute table. Ignition pod 1 will be indexed as 0 in the model. That is why we use 0 instead of 1 here.		
			}
			
			// ENVC parameter or w(e,ie) in the objective function: e is the new fire_id, i is the dynamic POD
			ENVC = new double[number_of_fires][];
			int row_index = 0;
			for (int e = 0; e < number_of_fires; e++) {
				ENVC[e] = new double[number_of_PODS[e]];
				for (int i = 0; i < number_of_PODS[e]; i++) {
					ENVC[e][i] = Double.parseDouble(data[row_index][4]);	// 'poly_core' column (currently we only consider net loss of core areas)
					row_index++;
				}
			}

			// Adjacent PODs
			adjacent_PODS = new ArrayList[number_of_fires][];
			row_index = 0;
			for (int e = 0; e < number_of_fires; e++) {
				adjacent_PODS[e] = new ArrayList[number_of_PODS[e]];
				for (int i = 0; i < number_of_PODS[e]; i++) {
					adjacent_PODS[e][i] = new ArrayList<Integer>();
					String[] AdjPODs = data[row_index][5].split(",");		// 'adj_polys' column
					for (String s : AdjPODs) {
						int pod_id = Integer.parseInt(s) - 1;	// because in the model poly index starts from 0
						adjacent_PODS[e][i].add(pod_id);
					}
					row_index++;
				}
			}
			

			// b_list stores all the breaks within the shared boundary of 2 adjacent polygons i and j of fire e
			b_list = new ArrayList[number_of_fires][][];
			for (int e = 0; e < number_of_fires; e++) {	
				b_list[e] = new ArrayList[number_of_PODS[e] - 1][];
				for (int i = 0; i < number_of_PODS[e] - 1; i++) {
					b_list[e][i] = new ArrayList[number_of_PODS[e]];
					for (int j = i + 1; j < number_of_PODS[e]; j++) {
						b_list[e][i][j] = new ArrayList<Integer>();
					}
				}
			}
			
			row_index = 0;
			for (int e = 0; e < number_of_fires; e++) {
				for (int i = 0; i < number_of_PODS[e]; i++) {					// loop all poly i (the first poly of the adjacent pair). If reading from "poly_id" column then we need to minus 1.
					String[] sim_polys = data[row_index][6].split(",");			// 'sim_polys' column
					String[] origin_break_ids = data[row_index][7].split(",");	// 'break_ids' column
					if (!sim_polys[0].equals("")) {								// means the "poly_id" has at least one adjacent poly. Note that sim_polys column only list poly j of the pair (i,j) where j > i to avoid double counting
						for (int k = 0; k < sim_polys.length; k++) {			// loop all poly j (the second poly of the adjacent pair, j > i automatically in the input)
							int j =  Integer.parseInt(sim_polys[k]) - 1;		// because in the model poly index starts from 0 so we need to minus 1 here
							// we now have a pair of polygon (i,j). In their shared bound there may be multiple fuel breaks, get them all:
							String[] original_break_ids_within_pods_bound = origin_break_ids[k].split(" ");		// all breaks are separated by space as how we create the input
							// Add all these breaks into the list
							for (String s : original_break_ids_within_pods_bound) {
								int break_id = Integer.parseInt(s) - 1;
								b_list[e][i][j].add(break_id);
							}
						}
					}
					row_index++;
				}
			}
					
			// fl_list stores flame lengths across all the break segments within the shared boundary of 2 adjacent polygons i and j of fire e (similar  to the above code)
			fl_list = new ArrayList[number_of_fires][][];
			for (int e = 0; e < number_of_fires; e++) {	
				fl_list[e] = new ArrayList[number_of_PODS[e] - 1][];
				for (int i = 0; i < number_of_PODS[e] - 1; i++) {
					fl_list[e][i] = new ArrayList[number_of_PODS[e]];
					for (int j = i + 1; j < number_of_PODS[e]; j++) {
						fl_list[e][i][j] = new ArrayList<Double>();
					}
				}
			}
			
			
			row_index = 0;
			for (int e = 0; e < number_of_fires; e++) {
				for (int i = 0; i < number_of_PODS[e]; i++) {					// loop all poly i (the first poly of the adjacent pair). If reading from "poly_id" column then we need to minus 1.
					String[] sim_polys = data[row_index][6].split(",");			// 'sim_polys' column
					String[] max_flame_lengths = data[row_index][8].split(",");	// 'max_fl' column
					if (!sim_polys[0].equals("")) {								// means the "poly_id" has at least one adjacent poly. Note that sim_polys column only list poly j of the pair (i,j) where j > i to avoid double counting
						for (int k = 0; k < sim_polys.length; k++) {			// loop all poly j (the second poly of the adjacent pair, j > i automatically in the input)
							int j =  Integer.parseInt(sim_polys[k]) - 1;		// because in the model poly index starts from 0 so we need to minus 1 here
							// we now have a pair of polygon (i,j). In their shared bound there may be multiple fuel breaks (each break segment has a max flame length), get them all:
							String[] breaks_max_fls = max_flame_lengths[k].split(" ");		// all flame lengths are separated by space as how we create the input
							// Add all these breaks into the list
							for (String s : breaks_max_fls) {
								double val = Double.parseDouble(s);
								fl_list[e][i][j].add(val);
							}
						}
					}
					row_index++;
				}
			}
			
			// Read input_2 -------------------------------------------------------------------------------------------------------------------------------------------
			list = Files.readAllLines(Paths.get(input_2_file.getAbsolutePath()), StandardCharsets.UTF_8);
			list.remove(0);	// Remove the first row (header)
			a = list.toArray(new String[list.size()]);
			total_rows = a.length;
			total_columns = a[0].split("\t").length;				
			data = new String[total_rows][total_columns];
		
			// read all values from all rows and columns
			for (int i = 0; i < total_rows; i++) {
				String[] rowValue = a[i].split("\t");
				for (int j = 0; j < total_columns; j++) {
					data[i][j] = rowValue[j];
				}
			}
			
			number_of_fuelbreaks = total_rows;
			q0 = new double[number_of_fuelbreaks]; 	// the current capacity of a fuel break
			d1 = new double[number_of_fuelbreaks]; 	// parameter for D1 variable
			d2 = new double[number_of_fuelbreaks]; 	// parameter for D2 variable
			d3 = new double[number_of_fuelbreaks]; 	// parameter for D3 variable
			for (int b = 0; b < number_of_fuelbreaks; b++) {
				q0[b] = Double.parseDouble(data[b][1]);
				d1[b] = Double.parseDouble(data[b][2]);
				d2[b] = Double.parseDouble(data[b][3]);
				d3[b] = Double.parseDouble(data[b][4]);
			}					
			
		
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	public int get_number_of_fires() {
		return number_of_fires;
	}
	
	public int[] get_original_fire_id() {
		return original_fire_id;
	}

	public int[] get_number_of_PODS() {
		return number_of_PODS;
	}
	
	public int[] get_n() {
		return n;
	}
	
	public int[] get_ignition_POD() {
		return ignition_POD;
	}
	
	public double[][] get_ENVC() {
		return ENVC;
	}
	
	public List<Integer>[][] get_adjacent_PODS() {
		return adjacent_PODS;
	}
	
	public int get_number_of_fuelbreaks() {
		return number_of_fuelbreaks;
	}
	
	public double[] get_q0() {
		return q0;
	}
	
	public double[] get_d1() {
		return d1;
	}
	
	public double[] get_d2() {
		return d2;
	}
	
	public double[] get_d3() {
		return d3;
	}
	
	public List<Integer>[][][] get_b_list() {
		return b_list;
	}
	
	public List<Double>[][][] get_fl_list() {
		return fl_list;
	}	
}
