package fuelbreakmodel;

public class Information_Variable {
	private String var_name;
	private int fire_ID, pod_ID, break_ID, magegement_option;

	public Information_Variable(String var_name) {
		this.var_name = var_name;
		String[] term = var_name.substring(2).split("_"); // remove first 2 letters and then split
		try {
			String first_letter_of_var_name = var_name.substring(0, 1);
			switch (first_letter_of_var_name) {
			case "D":
				break_ID = Integer.parseInt(term[0]);
				magegement_option = Integer.parseInt(term[1]);
				break;
			case "X":
				fire_ID = Integer.parseInt(term[0]);
				pod_ID = Integer.parseInt(term[1]);
				break;
			case "Q":
				break_ID = Integer.parseInt(term[0]);
				break;
			case "A":
				break_ID = Integer.parseInt(term[0]);
				break;
			case "C":
				break_ID = Integer.parseInt(term[0]);
				break;
			case "Y":
				break;
			default:
				break;
			}
		} catch (Exception e) {
		}
	}

	public String get_var_name() {
		return var_name;
	}

	public int get_fireID() {
		return fire_ID;
	}

	public int get_pod_ID() {
		return pod_ID;
	}
	
	public int get_break_ID() {
		return break_ID;
	}
	
	public int get_magegement_option() {
		return magegement_option;
	}
}
