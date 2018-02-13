/**
 *
 * @author kuryfs
 * ICC - Insight Coding Challenge
 * by Fabricio Kury - fabriciokury (<at>) gmail (<dot>) com
 * For an overview of this program, please see README.md.
 */
package icc;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.PriorityQueue;
import java.util.Collections;
import java.util.HashMap;


public class Icc {
    private static final int POSITION_CMTE_ID = 1;
    private static final int POSITION_NAME = 8;
    private static final int POSITION_ZIP_CODE = 11;
    private static final int POSITION_TRANSACTION_DT = 14;
    private static final int POSITION_TRANSACTION_AMT = 15;
    private static final int POSITION_OTHER_ID = 16;
    private static final int MAX_POSITION = POSITION_OTHER_ID; // Must be the highest POSITION_* value.
    private static final long MAX_TRANSACTION_AMT = (long)1e14;
    private static final String DELIMITER_CHARACTER = "|";
    private static final LocalDate MIN_TRANSACTION_DATE = LocalDate.of(1975, Month.JANUARY, 1); // See data dictionary.
    
    private static final HashMap<String, Integer> DONORS = new HashMap<String, Integer>();
    private static final HashMap<String, ContributionsQueue> RECIPIENT_GROUPS = new HashMap<>();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMddyyyy"); // As per data dictionary.
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##"); // For printing the sum (not rounded)
        // Using DecimalFormat can be a substantial overhead. An easy solution is to determine that the sum should
        // also be rounded to nearest dollar just like the percentile. The only reason for using DecimalFormat here
        // is to make it such that if the sum is a whole dollar amount, it gets printed without decimal digits.

    private static final double PROGRESS_REPORTING_MULTIPLIER = 1.4; // Speed of increase of report_progress_at
    private static int report_progress_at = 10000; // Program will report the number of input records processed at this
        // count, and then it will multiply this count by PROGRESS_REPORTING_MULTIPLIER.
    private static int percentile = 0;
    
    private static class ContributionsQueue {
        /* The purpose of this class is to ingest contributions in logarithmic time and return the percentile (defined
        in the constructor) and sum in constant time. It works by keeping two heaps (PriorityQueues), called "lower" and
        "upper," and maintaining their correct relative sizes. "lower" is a maxheap and contains the p% smallest
        contributions. "upper" is a minheap and contains the remainder. E.g. If there are 10 contributions,
        "lower" will contain the 7 smaller ones. Therefore the root of "lower" contains the percentile value.
        The variable "sum" keeps the precomputed sum of the contributions. */
        /* The contributions can have up to 2 decimal places, but here I am using integers (Longs) (by multiplying the
        contribution value by 100) to hopefully improve efficiency and to avoid pesky rounding errors. Conversion back
        to floating point (double) is performed by functions getPercentile() and getSum(). getPercentile() also rounds
        the number to nearest dollar as requested by the coding challenge. */
        
        // Contributions are stored as longs (not floating point) to improve efficiency and avoid rounding errors,
        // therefore you need to use getPercentile() and getSum() to access the computed numbers.
        
        private final PriorityQueue<Long> lower = new PriorityQueue<>(6, Collections.reverseOrder());
        private final PriorityQueue<Long> upper = new PriorityQueue<>(6);
        // The number 6 above is just the initial size of the heap. It gets automatically increased as needed. The magic
        // number of 6 is just a rough guess of an optimal balance between memory and speed.
        private final double percentile;
        private long sum=0;
        private int size=0;

        
        public ContributionsQueue(double percentile) {
            this.percentile = percentile/100;
        }
        
        
        private void add(Long l) throws Exception {
            if(size == Integer.MAX_VALUE)
                throw new Exception("Maximum values per recipient group is " + Integer.MAX_VALUE + ".");
            
            // Add the new value.
            if(lower.isEmpty() || l <= lower.peek())
                lower.add(l);
            else
                upper.add(l);
            size++;
            sum += l;
            
            // Move from one heap to another as needed to ensure that lower.peek() contains the desired percentile.
            long size_lower = (long)Math.ceil(size * percentile);
            if (lower.size() > size_lower)
                upper.add(lower.poll());
            else if (lower.size() < size_lower)
                lower.add(upper.poll());
        }
        
        
        public void add(Double d) throws Exception {
            add((long)(d*100));
        }
        
        
        public long getPercentile() {
            // As specified, percentiles must be rounded to nearest dollar.
            return Math.round(lower.peek()/100d);
        }
        
        
        public double getSum() {
            return sum/100d;
        }
    }
    
    
    private static void setPercentile(int new_percentile) throws Exception {
        // TODO: Rebuild all ContributionsQueues if changing the percentile after records have been ingested.
        if(new_percentile <= 0 || new_percentile > 100)
            throw new Exception("Percentile must be within interval (0,100].");
        else
            percentile = new_percentile;
    }

    
    public static LocalDate getDateFromString(String date_string) {
        // Date verification is straightforward: if DATE_FORMAT can parse it, it is valid. This includes sanity
        // checking, e.g. February 31st, 2010 is an invalid date.
        try {
            return DATE_FORMAT.parse(date_string).toInstant().
                atZone(ZoneId.systemDefault()).toLocalDate();
        } catch(ParseException ex) {
            return null;
        }
    }
    
    
    private static boolean isRepeatDonor(String NAME, String ZIP_CODE, int year) {
        // Each donor (identified by name+zip code) has an entry at HashMap DONORS, which contains the earliest year
        // of contribution from him/her. A repeat donor is someone who already contributed in a previous year.
        String donor_full_id = NAME + "=" + ZIP_CODE;
        Integer lowest_year = DONORS.get(donor_full_id);
        if(lowest_year == null || lowest_year > year) {
            DONORS.put(donor_full_id, year);
            return false;
        }
        else
            return true;
    }
    
    
    private static String ingestRecord(String CMTE_ID, String NAME, String ZIP_CODE, int year,
        Double amount) throws Exception {
        // This function assumes parameters are valid. It adds the record to its entry in HashMap RECIPIENT_GROUPS
        // and returns the text line ready to be written to output file.
        if(isRepeatDonor(NAME, ZIP_CODE, year)) {
            // Donor is a repeat donor.
            // Add the record to its recipient group.
            // Recipient groups are defined by CMTE_ID, zip code and transaction year.
            String recipient_group_id = CMTE_ID + "=" + ZIP_CODE + "=" + year;
            ContributionsQueue recipient_group = RECIPIENT_GROUPS.get(recipient_group_id);
            if(recipient_group == null)
                // This is a new recipient group.
                RECIPIENT_GROUPS.put(recipient_group_id, recipient_group = new ContributionsQueue(percentile));
            recipient_group.add(amount);
            // Return the output line.
            return CMTE_ID + "|" + ZIP_CODE + "|" + year + "|" + recipient_group.getPercentile() +
                "|" + DECIMAL_FORMAT.format(recipient_group.getSum()) + "|" + recipient_group.size + "\n";
        }
        else
            return null;
    }

    
    private static String parseInputLine(String line) throws Exception {
        // This function receives one line from itcont.txt, finds and verifies the fields, then calls ingestRecord().
        String CMTE_ID = null, NAME = null, ZIP_CODE = null;
        int year = 0;
        double amount = 0;
        // Code will seek the delimiter characters and recognize the fields by their numeric positions as defined
        // in the data dictionary. NOTICE: The criteria for assessing the validity of each field are hardcoded here.
        // Previously I had coded exceptions for each invalid field, but later I dropped to improve efficiency.
        int cur_index, last_index = 0;
        for(int position = 1; position <= MAX_POSITION; position++) {
            cur_index = line.indexOf(DELIMITER_CHARACTER, last_index);
            if(cur_index == -1)
                // Unable to find all required fields from the file. Record will be ignored because it
                // didn't reach case 16 in the switch below.
                return null;//throw new Exception("Unable to find all required fields.");

            switch(position) {
                // In this switch the code uses cur_index and last_index to very quickly verify the length of the
                // fields. The idea is to reject as fast as possible any record that couldn't possibly be valid.
                case POSITION_CMTE_ID: // CTME_ID
                    if(cur_index != last_index + 9)
                        // ID is not 9 characters long.
                        return null;//throw new Exception("CMTE_ID is not 9 characters long.");
                    CMTE_ID = line.substring(last_index, cur_index);
                    
                    // Verify CMTE_ID: All characters must be alphanumeric.
                    for(int i=0; i < CMTE_ID.length(); i++)
                        if(!Character.isLetterOrDigit(CMTE_ID.charAt(i)))
                            // Some character inside the string code is not a digit nor letter. This is invalid.
                            return null;//throw new Exception("CMTE_ID must be alphanumeric.");
                    break;
                case POSITION_NAME: // NAME
                    if(cur_index==last_index || cur_index-last_index > 200)
                        // Name is empty or too big.
                        return null;
                    NAME = line.substring(last_index, cur_index);
                    break;
                case POSITION_ZIP_CODE: // ZIP_CODE
                    if(cur_index != last_index+9 && cur_index != last_index+5)
                        // ZIP code is empty or too big.
                        return null;//throw new Exception("ZIP code must be 5 or 9 digits, but " +
                            //(cur_index-last_index) + " were found.");
                    ZIP_CODE = line.substring(last_index, cur_index);
                    
                    // Verify ZIP code: Must be either 9 or 5 characters, first 5 characters must be digits.
                    if(ZIP_CODE.length() == 9)
                        ZIP_CODE = ZIP_CODE.substring(0, 5);
                    
                    for(int i=0; i < ZIP_CODE.length(); i++)
                        if(!Character.isDigit(ZIP_CODE.charAt(i)))
                            // Some character inside the ZIP code is not a digit. Invalid ZIP code.
                            return null;//throw new Exception("ZIP code must be strictly numeric.");        
                    break;
                case POSITION_TRANSACTION_DT: // TRANSACTION_DT
                    if(cur_index != last_index + 8)
                        // Date is not possibly formatted as MMddyyyy.
                        return null;//throw new Exception("Invalid date, format must be MMddyyyy.");

                    // Verify date: must be able parsed by DATE_FORMAT and must be between minimum and current dates.
                    { // Create scope to restrict variable LocalDate date.
                        LocalDate date = getDateFromString(line.substring(last_index, cur_index));
                        if(date == null)
                            // Unable to parse the date or date is invalid (after current date or before minimum).
                            return null;//throw new Exception("Invalid date format or range. Format must be MMddyyyy.");
                        if(date.isAfter(LocalDate.now()) || date.isBefore(MIN_TRANSACTION_DATE))
                            // Unable to parse the date or date is invalid (after current date or before minimum).
                            return null;//throw new Exception("Date must be between " + 
                                //MIN_TRANSACTION_DATE.toString() + " and today.");
                        year = date.getYear();
                    }
                    break;
                case POSITION_TRANSACTION_AMT: // TRANSACTION_AMT
                    if(cur_index==last_index || cur_index-last_index > 16+1)
                        // Amount is empty or bigger than NUMBER(14,2). The +1 is to account for the decimal separator.
                        return null;//throw new Exception("Invalid transaction amount.");
                    
                    // Verify amount: must be succesfully parsed into a double bigger than zero and smaller than 10^15,
                    // because the maximum number possible in NUMBER(14,2) is 10^14 - 0.01.
                    amount = Double.parseDouble(line.substring(last_index, cur_index));
                    if(amount <= 0 || amount >= MAX_TRANSACTION_AMT)
                        return null;//throw new Exception(
                            //"Transaction amount must be within (0, " + MAX_TRANSACTION_AMT + ").");
                    break;
                case POSITION_OTHER_ID: // OTHER_ID
                    if(cur_index == last_index)
                        // OTHER_ID is empty. This is good.
                        // This code here is using the last field (OTHER_ID) as a confirmation that the
                        // *parsing* itself looks successful, i.e. each line must arrive at this point.
                        return ingestRecord(CMTE_ID, NAME, ZIP_CODE, year, amount);
                    break;
            }
            last_index = cur_index+1;
        }
        
        return null;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        DATE_FORMAT.setLenient(false); // Date format must be strictly as specified in data dictionary.
        
        if(args.length != 3) {
            System.err.println("Program must receive three arguments: itcont.txt percentile.txt output.txt");
            System.exit(1);
        }
        
        // Read file with the percentile.
        try {
            setPercentile(Integer.parseInt((new BufferedReader(new FileReader(args[1]))).readLine()));
        } catch(Exception ex) {
            System.err.println("Unable to read percentile file: " + ex.getMessage());
            System.exit(1);
        }
        
        // Open output file for writing.
        try(BufferedWriter outputWriter = new BufferedWriter(new FileWriter(args[2]))) {
            // Read the file with the actual records, line by line.
            try(BufferedReader br = new BufferedReader(new FileReader(args[0]))) {
                String line, output_line = null;
                long lines_processed = 0;
                while((line = br.readLine()) != null) {
                    try {
                        output_line = parseInputLine(line);
                    } catch(Exception ex) {
                        System.err.println("Error processing line " + (lines_processed+1) + ": " + ex.getMessage());
                    }
                    
                    if(output_line != null) {
                        outputWriter.write(output_line);
                        output_line = null;
                    }
                    
                    if(++lines_processed%report_progress_at == 0) {
                        report_progress_at *= PROGRESS_REPORTING_MULTIPLIER;
                        System.out.println(lines_processed + " lines processed. Still working.");
                    }
                }
                System.out.println(lines_processed + " lines processed. Processing complete.");
            }  catch(IOException ex) {
                System.err.println("Unable to read itcont file: " + ex.getMessage());
                System.exit(1);
            }
        } catch(IOException ex) {
            System.err.print("Unable to write to output file: " + ex.getMessage());
            System.exit(1);
        }
    }    
}
