package fuelbreakmodel;

public class Information_Variable {
	private String var_name;
	private int fireID, pod_ID;

	public Information_Variable(String var_name) {
		this.var_name = var_name;
		String[] term = var_name.substring(2).split("_"); // remove first 2 letters and then split
		try {
			String first_letter_of_var_name = var_name.substring(0, 1);
			switch (first_letter_of_var_name) {
			case "X":
				fireID = Integer.parseInt(term[0]);
				pod_ID = Integer.parseInt(term[1]);
				break;
			case "Q":
				break;
			case "D":
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
		return fireID;
	}

	public int get_pod_ID() {
		return pod_ID;
	}
}
