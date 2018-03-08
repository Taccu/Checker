package tools;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.ldif.LdapLdifException;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;

public class Checker {
	private HashSet<CSVRecord> csv = new HashSet<>();
	private ArrayList<LdifEntry> ldifEntries = new ArrayList<>();
	private ArrayList<Group> groups = new ArrayList<>();
	
	public Checker(HashSet<CSVRecord> csv, ArrayList<LdifEntry> ldifEntries) {
		this.csv = csv;
		this.ldifEntries = ldifEntries;
	}

	public static void main(String[] args) throws IOException, LdapLdifException {
		Reader in = new FileReader("D:\\3rdParty\\Test\\OTDS\\opendj\\csv.csv");
		Iterable<CSVRecord> records = CSVFormat.EXCEL.withDelimiter(';').withIgnoreEmptyLines().withFirstRecordAsHeader().withHeader("groupId", "stringList").parse(in);
		HashSet<CSVRecord> csv = new HashSet<>();
		for(CSVRecord record : records) {
			csv.add(record);
		}
		ArrayList<LdifEntry> ldifEntries = new ArrayList<>();
		LdifReader reader = new LdifReader();
		List<LdifEntry> list = reader.parseLdifFile("D:\\3rdParty\\Test\\OTDS\\opendj\\otds-16.ldif");
		reader.close();
		for(LdifEntry entry : list) {
			ldifEntries.add(entry);
		}
		new Checker(csv,ldifEntries).check();
	}
	
	public void check() {
		for(CSVRecord csvRecord : csv) {
			if(csvRecord.size() == 2) {
				Group a = new Group(csvRecord.get(0));
				Group b = new Group(csvRecord.get(1));
				if(groups.contains(a)) {
					a = groups.get(groups.indexOf(a));
				}
				else {
					System.out.println("New group: " + a.getDn());
					groups.add(a);
				}
				if(groups.contains(b)) {
					b = groups.get(groups.indexOf(b));
				}
				else {

					System.out.println("New group: " + b.getDn());
					groups.add(b);
				}
				a.addShouldHasMember(b);
				b.addShouldBeMemberOf(a);
			}
		}
		
		for(Group group : groups) {
			//Check for existance
			if(checkIfGroupExists(group, ldifEntries) || checkWithGuessIfGroupExists(group, ldifEntries)) {
				group.doesExist();
			}
			if(!group.exists) {
				continue;
			}
			for(Group shouldBeMemberOf : group.getShouldBeMemberOf()) {
				if(checkForAttribute(shouldBeMemberOf, "otmemberOf",ldifEntries)) {
					group.addIsMember(shouldBeMemberOf);
				}
			}
			for(Group shouldHasMember : group.getShouldHasMember()) {
				if(checkForAttribute(shouldHasMember, "otmember",ldifEntries)) {
					group.addMember(shouldHasMember);
				}
			}
			System.out.println(group.checkIfCorrect());
		}
	}

	private String extractString(String regex, String input) {
		Pattern p = Pattern.compile(regex);
		Matcher matcher = p.matcher(input);
		if(!matcher.find()) {
			return null;
		}
		if(matcher.group().startsWith("@")) {
			return matcher.group().substring(1, matcher.group().length()-1);
		}
		else {
			return matcher.group().substring(0, matcher.group().length()-1);
		}
	}
	
	private boolean checkForAttribute(Group group, String lookFor, ArrayList<LdifEntry> ldifEntries) {
		for(LdifEntry ldifEntry : ldifEntries) {
			Attribute attr = ldifEntry.get(lookFor);
			if(attr != null) {
				Iterator<Value<?>> it = attr.iterator();
				while(it.hasNext()) {
					Object nextValue = it.next();
					if(nextValue instanceof Value<?>) {
						Value<?> value = (Value<?>) nextValue;
						String string = value.getValue().toString();
						if(group.getDn().equals(string)) {
							return true;
						}
					}
				}
			}
		}	
		return false;
	}

	private boolean checkIfGroupExists(Group group, ArrayList<LdifEntry> ldifEntries) {
		for(LdifEntry ldifEntry : ldifEntries) {
			if(ldifEntry.getDn().getName().equals(group.getDn())) {
				return true;
			}
		}
		return false;
	}
	
	private boolean checkWithGuessIfGroupExists(Group group,ArrayList<LdifEntry> ldifEntries) {
		String cn = group.getCn(),ou = group.getOu();
		if(cn== null || ou == null) {
			return false;
		}
		if(cn != null && ou != null) {
			for(LdifEntry ldifEntry : ldifEntries) {
				String guess = extractString(cn + "(.*?)" + ou + "(.*?)", ldifEntry.getDn().getName());
				if(guess != null) {
					return true;
				}
			}
		}
		return false;
	}

	class Group extends Object{
		String dn,cn,ou;
		HashSet<Group> isMemberOf = new HashSet<>();
		HashSet<Group> hasMember = new HashSet<>();
		HashSet<Group> shouldBeMemberOf = new HashSet<>();
		HashSet<Group> shouldHasMember = new HashSet<>();
		boolean exists = false;
		public Group(String dn) {
			this.dn = dn;
			this.cn = createCn(dn);
			this.ou = createOu(dn);
		}
		
		public void doesExist() {
			exists = true;
		}
		
		public boolean exists() {
			return exists;
		}
		
		public String getCn() {
			return cn;
		}
		
		public String getOu() {
			return ou;
		}
		
		private String createCn(String groupName) {
			return extractString("(.*?)@", groupName);
		}
		
		private String createOu(String groupName) {
			return extractString("@(.*?)\\.", groupName);
		}
		
		public String getDn() {
			return dn;
		}
		
		public void addShouldHasMember(Group group) {
			shouldHasMember.add(group);
		}
		
		public void addShouldBeMemberOf(Group group) {
			shouldBeMemberOf.add(group);
		}
		
		public void addIsMember(Group group) {
			isMemberOf.add(group);
		}
		
		public void addMember(Group group) {
			hasMember.add(group);
		}
		
		public boolean groupShouldHasMember(Group group) {
			if(shouldHasMember.contains(group)) {
				return true;
			}
			return false;
		}
		
		public boolean groupShouldMemberOf(Group group) {
			if(shouldBeMemberOf.contains(group)) {
				return true;
			}
			return false;
		}
		
		public boolean groupIsMember(Group group) {
			if(isMemberOf.contains(group)) {
				return true;
			}
			return false;
		}
		
		public boolean groupHasMember(Group group) {
			if(hasMember.contains(group)) {
				return true;
			}
			return false;
		}
		
		public HashSet<Group> getShouldBeMemberOf() {
			return shouldBeMemberOf;
		}
		
		public HashSet<Group> getShouldHasMember() {
			return shouldHasMember;
		}
		
		public HashSet<Group> getMember() {
			return hasMember;
		}
		
		public HashSet<Group> getMemberOf() {
			return isMemberOf;
		}
		@Override
		public String toString() {
			return dn;
		}
		
		public String checkIfCorrect() {
			
			if(!exists()) {
				return "Group does not exist.";
			}
			/*HashSet<Group> a = getShouldBeMemberOf();
			HashSet<Group> b = getMemberOf();
			if(a.size() != b.size()) {
				if(a.size() > b.size()) {
					a.removeAll(b);
					StringBuilder builder = new StringBuilder();
					builder.append("FAIL: This group ");
					builder.append(getDn());
					builder.append(" is missing in OTDS as Member of ");
					builder.append(":");
					for(Group group : a) {
						builder.append(group.getDn());
						builder.append("|");
					}
					System.out.println(getCn() +"|"+ getOu());
					return builder.toString();
				}
				else {
					//Shouldn't happen
					return "Fail" + dn;
				}
			}*/
			HashSet<Group> c = getShouldHasMember();
			HashSet<Group> d = getMember();
			if(c.size() != d.size()) {
				if(c.size() > d.size()) {
					c.removeAll(d);
					StringBuilder builder = new StringBuilder();
					builder.append("FAIL: These groups ");
					for(Group group : c) {
						builder.append(group.getDn());
						builder.append("|");
					}
					builder.append("are missing in OTDS as Member for ");
					builder.append(getDn());
					return builder.toString();
				}
				else {
					//Shouldn't happen
					return "Fail" + dn;
				}
			}
			return "Success:" + dn ;
		}
		
		
		@Override
		public boolean equals(Object o) {
			if(o instanceof Group) {
				Group otherGroup = (Group) o;
				return otherGroup.getDn().equals(this.dn);
			}
			else {
				return false;
			}
		}
	}
}
