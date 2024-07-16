package fuelbreakmodel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GreatBasin_2_inputs_data_processing {
	private int number_of_fires;	// number of fires
	private int[] original_fire_id; // the original fire_id: which is "fire_id" in the input_01_file of the example, or "FIRE_NUMBE" in the great basin attribute table
	private int[] number_of_PODS; 	// number of dynamic PODs for each fire
	private int[] n; 				// number of dynamic PODs for each fire minus one
	private int[] ignition_POD; 	// the ignition POD of each fire
												
	private double[][] core_areas;				// w(e,ie) in the objective function, with e is the FireID, i is the dynamic POD			
	private List<Integer>[][] adjacent_PODS;	// Adjacent PODs
					
	private int number_of_fuelbreaks;
	private double[] break_length;
	private double[][] break_area;	// maintenance area of fuel break b when management option k is implemented.
	private int number_of_management_options;
	private double[] q_0; 	// flame length capacity of a fuel break when no investment is made for that break
	private double[][] q; 	// flame length capacity of a fuel break when a management option k is implemented 
	private double[][] c; 	// core areas impacted by a fuel break when a management option k is implemented 										
	
	private List<Integer>[][][] b_list;	// b_list stores all the breaks within the shared boundary of 2 adjacent polygons i and j of fire e										
	private List<Double>[][][] fl_list;	// fl_list stores flame lengths across all the break segments within the shared boundary of 2 adjacent polygons i and j of fire e		

	public String[][] get_data_excluding_larest_fires(String[][] data, double fire_size_percentile) {		// 0.8 mean keep 80% and remove 20% largest fires
		// Note that the input has been sorted by increasing values of "FIRE_NUMBE" and "poly_id"
		List<Integer> original_fire_id_list = new ArrayList<Integer>();	
		List<Double> trim_size_list = new ArrayList<Double>();	

		number_of_fires = 0;
		for (int i = 0; i < data.length; i++) {
			int origin_id = Integer.parseInt(data[i][0]);				// 'FIRENUMBE' column
			double trim_size_value = Double.parseDouble(data[i][2]);	// 'trim_size' column
			if (!original_fire_id_list.contains(origin_id)) {
				original_fire_id_list.add(origin_id);
				trim_size_list.add(trim_size_value);
				number_of_fires++;
			}
		}
		double[] trim_size = trim_size_list.stream().mapToDouble(Double::doubleValue).toArray();	// convert list to array
		Arrays.sort(trim_size);
		
		int percentile_index = (int) Math.round(Math.floor(trim_size.length * fire_size_percentile));	// Calculate the percentile index
		double percentile_trim_size_threshole = trim_size[percentile_index];
		
		// count the number of row that would be included
		int count = 0;		
		for (String[] row : data) {
		    double trim_size_value = Double.parseDouble(row[2]);
		    if (trim_size_value <= percentile_trim_size_threshole) {
		        count++;
		    }
		}
		
		// add the included data to new_data
		String[][] trim_data = new String[count][];
		count = 0;
		for (String[] row : data) {
			double trim_size_value = Double.parseDouble(row[2]);
			if (trim_size_value <= percentile_trim_size_threshole) {
				trim_data[count] = row;
				count++;
		    }
		}
		
		System.out.println("number of rows in the original data = " + data.length);
		System.out.println("number of rows included in the trimmed data = " + trim_data.length);
		return trim_data;
	}
	
	public GreatBasin_2_inputs_data_processing(File input_1_file, File input_2_file, boolean excluding_largest_fires) {
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
			
			if (excluding_largest_fires) {
				data = get_data_excluding_larest_fires(data, 0.8);
				total_rows = data.length;
			}
			
			List<Integer> original_fire_id_list = new ArrayList<Integer>();	
			List<Integer> num_of_PODS_list = new ArrayList<Integer>();	

			number_of_fires = 0;
			for (int i = 0; i < total_rows; i++) {
				int origin_id = Integer.parseInt(data[i][0]);				// 'FIRENUMBE' column
				int num_pods = Integer.parseInt(data[i][15]);				// 'num_polys' column
				if (!original_fire_id_list.contains(origin_id)) {
					original_fire_id_list.add(origin_id);
					num_of_PODS_list.add(num_pods);
					number_of_fires++;
				}
			}
			System.out.println("number of fires in the model = " + number_of_fires);
			original_fire_id = Stream.of(original_fire_id_list.toArray(new Integer[original_fire_id_list.size()])).mapToInt(Integer::intValue).toArray();	// convert list to array
			number_of_PODS = Stream.of(num_of_PODS_list.toArray(new Integer[num_of_PODS_list.size()])).mapToInt(Integer::intValue).toArray();	// convert list to array		
			n = new int[number_of_fires]; 				// number of dynamic PODs for each fire minus one
			ignition_POD = new int[number_of_fires]; 	// the ignition POD of each fire
			for (int e = 0; e < number_of_fires; e++) {
				n[e] = number_of_PODS[e] - 1;
				ignition_POD[e] = 1 - 1;	// poly_id = 1 is always the Ignition pod in the attribute table. Ignition pod 1 will be indexed as 0 in the model. That is why we use 0 instead of 1 here.		
			}
			
			// w(e,ie) in the objective function: e is the new fire_id, i is the dynamic POD
			core_areas = new double[number_of_fires][];
			int row_index = 0;
			for (int e = 0; e < number_of_fires; e++) {
				core_areas[e] = new double[number_of_PODS[e]];
				for (int i = 0; i < number_of_PODS[e]; i++) {
					core_areas[e][i] = Double.parseDouble(data[row_index][9]);	// 'poly_core' column (currently we only consider net loss of core areas)
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
					String[] AdjPODs = data[row_index][10].split(",");		// 'adj_polys' column
					for (String s : AdjPODs) {
						if (!s.equals("")) {		// NOTE NOTE NOTE NOTE NOTE NOTE: This is not in the class Example_2_inputs_data_processing because the example is perfect. In reality, a fire may not be split by breaks, therefore having no adjacent polygons.
							int pod_id = Integer.parseInt(s) - 1;	// because in the model poly index starts from 0
							adjacent_PODS[e][i].add(pod_id);
						}
					}
					row_index++;
				}
			}
			

			// b_list stores all the breaks within the shared boundary of 2 adjacent polygons i and j of fire e. Note that we use split with -1 to include empty string in the result
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
					String[] sim_polys = data[row_index][11].split(",");		// 'sim_polys' column
					String[] origin_break_ids = data[row_index][12].split(",", -1);	// 'break_ids' column
					if (!(sim_polys[0].equals("") && sim_polys.length == 1)) {	// means the "poly_id" has at least one adjacent poly. Note that sim_polys column only list poly j of the pair (i,j) where j > i to avoid double counting
						for (int k = 0; k < sim_polys.length; k++) {			// loop all poly j (the second poly of the adjacent pair, j > i automatically in the input)
							int j =  Integer.parseInt(sim_polys[k]) - 1;		// because in the model poly index starts from 0 so we need to minus 1 here
							// we now have a pair of polygon (i,j). In their shared bound there may be multiple fuel breaks, get them all:
							String[] original_break_ids_within_pods_bound = origin_break_ids[k].split(" ", -1);		// all breaks are separated by space as how we create the input
							// Add all these breaks into the list
							for (String s : original_break_ids_within_pods_bound) {
								// if the 2 polygon share a vertex (a point) then this is "" in the input.
								// We use a dirty trick to add the first break (although it is not true) and add -9999 as flame length to not allow fire spreading through
								if (s.equals("")) {		
									b_list[e][i][j].add((int) 0);
								} else {
									int break_id = Integer.parseInt(s) - 1;
									b_list[e][i][j].add(break_id);
								}
							}
						}
					}
					row_index++;
				}
			}
					
			// fl_list stores flame lengths across all the break segments within the shared boundary of 2 adjacent polygons i and j of fire e. Note that we use split with -1 to include empty string in the result (similar  to the above code)
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
					String[] sim_polys = data[row_index][11].split(",");		// 'sim_polys' column
					String[] max_flame_lengths = data[row_index][13].split(",", -1);// 'max_fl' column
					if (!(sim_polys[0].equals("") && sim_polys.length == 1)) {	// means the "poly_id" has at least one adjacent poly. Note that sim_polys column only list poly j of the pair (i,j) where j > i to avoid double counting
						for (int k = 0; k < sim_polys.length; k++) {			// loop all poly j (the second poly of the adjacent pair, j > i automatically in the input)
							int j =  Integer.parseInt(sim_polys[k]) - 1;		// because in the model poly index starts from 0 so we need to minus 1 here
							// we now have a pair of polygon (i,j). In their shared bound there may be multiple fuel breaks (each break segment has a max flame length), get them all:
							String[] breaks_max_fls = max_flame_lengths[k].split(" ", -1);		// all flame lengths are separated by space as how we create the input
							// Add all these breaks into the list
							for (String s : breaks_max_fls) {
								// if the 2 polygon share a vertex (a point) then this is "" in the input.
								// We use a dirty trick to add the first break (although it is not true) and add -9999 as flame length to not allow fire spreading through
								if (s.equals("")) {		
									fl_list[e][i][j].add((double) -9999);
								} else {
									fl_list[e][i][j].add(Double.parseDouble(s));
								}
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
			number_of_management_options = 4;			// note k = 0, 1, 2, 3 in the model associated with k = 1, 2, 3, 4 in the manuscript_16_v4
			break_length = new double[number_of_fuelbreaks];
			break_area = new double[number_of_fuelbreaks][];
			q_0 = new double[number_of_fuelbreaks]; 	// current capacity of a fuel break when it is not invested in maintenance
			q = new double[number_of_fuelbreaks][]; 	// capacity of a fuel break when a management option k is implemented 
			c = new double[number_of_fuelbreaks][]; 	// corea areas impacted by a fuel break when a management option k is implemented 
			for (int b = 0; b < number_of_fuelbreaks; b++) {
				break_area[b] = new double[number_of_management_options];	// 4 options k = 0, 1, 2, 3 associated with break width 100, 200, 300, 400 Feet
				q[b] = new double[number_of_management_options];			// 4 options k = 0, 1, 2, 3 associated with break width 100, 200, 300, 400 Feet
				c[b] = new double[number_of_management_options];			// 4 options k = 0, 1, 2, 3 associated with break width 100, 200, 300, 400 Feet
				
				break_length[b] = Double.parseDouble(data[b][5]);
				
				break_area[b][0] = Double.parseDouble(data[b][6]);
				break_area[b][1] = Double.parseDouble(data[b][7]);
				break_area[b][2] = Double.parseDouble(data[b][8]);
				break_area[b][3] = Double.parseDouble(data[b][9]);
				
				c[b][0] = Double.parseDouble(data[b][10]);
				c[b][1] = Double.parseDouble(data[b][11]);
				c[b][2] = Double.parseDouble(data[b][12]);
				c[b][3] = Double.parseDouble(data[b][13]);
				
				q_0[b] = Double.parseDouble(data[b][14]);	// currently not use in our model, if we use it then we need another do nothing k option and change equation 2a to use = instead of <=
				
				q[b][0] = Double.parseDouble(data[b][15]);
				q[b][1] = Double.parseDouble(data[b][16]);
				q[b][2] = Double.parseDouble(data[b][17]);
				q[b][3] = Double.parseDouble(data[b][18]);
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
	
	public double[][] get_core_areas() {
		return core_areas;
	}
	
	public List<Integer>[][] get_adjacent_PODS() {
		return adjacent_PODS;
	}
	
	public int get_number_of_fuelbreaks() {
		return number_of_fuelbreaks;
	}
	
	public double[] get_break_length() {
		return break_length;
	}
	
	public double[][] get_break_area() {
		return break_area;
	}
	
	public int get_number_of_management_options() {
		return number_of_management_options;
	}
	
	public double[] get_q_0() {
		return q_0;
	}
	
	public double[][] get_q() {
		return q;
	}
	
	public double[][] get_c() {
		return c;
	}
	
	public List<Integer>[][][] get_b_list() {
		return b_list;
	}
	
	public List<Double>[][][] get_fl_list() {
		return fl_list;
	}	
}
