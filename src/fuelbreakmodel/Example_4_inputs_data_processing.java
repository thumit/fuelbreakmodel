package fuelbreakmodel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Example_4_inputs_data_processing {
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

	public Example_4_inputs_data_processing(File input_1_file, File input_2_file, File input_3_file, File input_4_file) {
		try {
			// Read input1 --------------------------------------------------------------------------------------------
			List<String> list = Files.readAllLines(Paths.get(input_1_file.getAbsolutePath()), StandardCharsets.UTF_8);
			list.remove(0);	// Remove the first row (header)
			String[] a = list.toArray(new String[list.size()]);
			int total_rows = a.length;
			int total_columns = a[0].split("\t").length;				
			int[][] data = new int[total_rows][total_columns];
		
			// read all values from all rows and columns
			for (int i = 0; i < total_rows; i++) {
				String[] rowValue = a[i].split("\t");
				for (int j = 0; j < total_columns; j++) {
					data[i][j] = Integer.parseInt(rowValue[j]);
				}
			}
			
			number_of_fires = total_rows;				// number of fires
			original_fire_id = new int[number_of_fires];// the original fire_id: which is "fire_id" in the input_01_file of the example, or "FIRE_NUMBE" in the great basin attribute table
			number_of_PODS = new int[number_of_fires]; 	// number of dynamic PODs for each fire
			n = new int[number_of_fires]; 				// number of dynamic PODs for each fire minus one
			ignition_POD = new int[number_of_fires]; 	// the ignition POD of each fire
			for (int e = 0; e < number_of_fires; e++) {
				original_fire_id[e] = data[e][0];	// we will print the original fire id in the problem and solution files.
				number_of_PODS[e] = data[e][1];
				n[e] = number_of_PODS[e] - 1;
				ignition_POD[e] = data[e][2] - 1;	// Ignition POD 1 will be indexed as 0 in the model		
			}
	
			
			// Read input2 --------------------------------------------------------------------------------------------
			list = Files.readAllLines(Paths.get(input_2_file.getAbsolutePath()), StandardCharsets.UTF_8);
			list.remove(0);	// Remove the first row (header)
			a = list.toArray(new String[list.size()]);
			total_rows = a.length;
			total_columns = a[0].split("\t").length;				
			String[][] string_data = new String[total_rows][total_columns];
		
			// read all values from all rows and columns
			for (int i = 0; i < total_rows; i++) {
				String[] rowValue = a[i].split("\t");
				for (int j = 0; j < total_columns; j++) {
					string_data[i][j] = rowValue[j];
				}
			}
			
			// get NPC parameters
			ENVC = new double[number_of_fires][];	// w(e,ie) or ENVC, with e is the FireID, i is the dynamic POD
			for (int e = 0; e < number_of_fires; e++) {
				ENVC[e] = new double[number_of_PODS[e]];
			}
			
			for (int i = 0; i < total_rows; i++) {
				int ee = Integer.parseInt(string_data[i][0]) - 1;
				int ii = Integer.parseInt(string_data[i][1]) - 1;
				ENVC[ee][ii] = Double.parseDouble(string_data[i][2]);
			}
	
			// Adjacent PODs
			adjacent_PODS = new ArrayList[number_of_fires][];
			for (int e = 0; e < number_of_fires; e++) {	
				adjacent_PODS[e] = new ArrayList[number_of_PODS[e]];
				for (int i = 0; i < number_of_PODS[e]; i++) {
					adjacent_PODS[e][i] = new ArrayList<Integer>();
				}
			}
			for (int i = 0; i < total_rows; i++) {
				int ee = Integer.parseInt(string_data[i][0]) - 1;
				int ii = Integer.parseInt(string_data[i][1]) - 1;
				String[] AdjPODs = string_data[i][3].split(",");
				for (String s : AdjPODs) {
					int id = Integer.parseInt(s) - 1;
					adjacent_PODS[ee][ii].add(id);
				}
			}
			
			
			// Read input4 --------------------------------------------------------------------------------------------
			list = Files.readAllLines(Paths.get(input_4_file.getAbsolutePath()), StandardCharsets.UTF_8);
			list.remove(0);	// Remove the first row (header)
			a = list.toArray(new String[list.size()]);
			total_rows = a.length;
			total_columns = a[0].split("\t").length;				
			string_data = new String[total_rows][total_columns];
		
			// read all values from all rows and columns
			for (int i = 0; i < total_rows; i++) {
				String[] rowValue = a[i].split("\t");
				for (int j = 0; j < total_columns; j++) {
					string_data[i][j] = rowValue[j];
				}
			}
			
			number_of_fuelbreaks = total_rows;
			q0 = new double[number_of_fuelbreaks]; 	// the current capacity of a fuel break
			d1 = new double[number_of_fuelbreaks]; 	// parameter for D1 variable
			d2 = new double[number_of_fuelbreaks]; 	// parameter for D2 variable
			d3 = new double[number_of_fuelbreaks]; 	// parameter for D3 variable
			for (int b = 0; b < number_of_fuelbreaks; b++) {
				q0[b] = Double.parseDouble(string_data[b][1]);
				d1[b] = Double.parseDouble(string_data[b][2]);
				d2[b] = Double.parseDouble(string_data[b][3]);
				d3[b] = Double.parseDouble(string_data[b][4]);
			}					
			
			
			// Read input3 --------------------------------------------------------------------------------------------
			list = Files.readAllLines(Paths.get(input_3_file.getAbsolutePath()), StandardCharsets.UTF_8);
			list.remove(0);	// Remove the first row (header)
			a = list.toArray(new String[list.size()]);
			total_rows = a.length;
			total_columns = a[0].split("\t").length;				
			string_data = new String[total_rows][total_columns];
		
			// read all values from all rows and columns
			for (int i = 0; i < total_rows; i++) {
				String[] rowValue = a[i].split("\t");
				for (int j = 0; j < total_columns; j++) {
					string_data[i][j] = rowValue[j];
				}
			}
			
			// Note add POD ID in the file - 1 so all PODs starts from 0, also FuelBreakIDs in the file - 1 so all breaks starts from 0
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
			
			for (int i = 0; i < total_rows; i++) {
				int ee = Integer.parseInt(string_data[i][0]) - 1;
				String[] pairPODs = string_data[i][2].split(",");
				int ii = Integer.parseInt(pairPODs[0]) - 1;
				int jj = Integer.parseInt(pairPODs[1]) - 1;
				String[] FuelBreakIDs = string_data[i][1].split(",");
				for (String s : FuelBreakIDs) {
					int id = Integer.parseInt(s) - 1;
					b_list[ee][ii][jj].add(id);
				}
			}
			
			// fl_list stores flame lengths across all the break segments within the shared boundary of 2 adjacent polygons i and j of fire e
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
			
			for (int i = 0; i < total_rows; i++) {
				int ee = Integer.parseInt(string_data[i][0]) - 1;
				String[] pairPODs = string_data[i][2].split(",");
				int ii = Integer.parseInt(pairPODs[0]) - 1;
				int jj = Integer.parseInt(pairPODs[1]) - 1;
				String[] breaks_max_fls = string_data[i][3].split(",");
				for (String s : breaks_max_fls) {
					double val = Double.parseDouble(s);
					fl_list[ee][ii][jj].add(val);
				}
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
