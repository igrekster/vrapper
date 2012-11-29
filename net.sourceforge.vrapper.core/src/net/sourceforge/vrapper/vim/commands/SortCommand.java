package net.sourceforge.vrapper.vim.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import net.sourceforge.vrapper.platform.SimpleConfiguration;
import net.sourceforge.vrapper.utils.IgnoreCaseStringComparator;
import net.sourceforge.vrapper.utils.NumericStringComparator;
import net.sourceforge.vrapper.vim.EditorAdaptor;

/**
 * 7. Sorting text						*sorting*
 * Vim has a sorting function and a sorting command.  The sorting function can be
 *   *:sor* *:sort*
 *    :[range]sor[t][!] [i][u][r][n][b][x][o] [/{pattern}/]
 * Sort lines in [range].  When no range is given all
 * lines are sorted.
 * 
 * With [!] the order is reversed.
 * 
 * With [i] case is ignored.
 * 
 * With [n] sorting is done on the first decimal number
 * in the line (after or inside a {pattern} match).
 * One leading '-' is included in the number.
 * 
 * NOT ORIGINALLY PART OF VIM:
 * With [b] sorting is done on the first binary
 * number in the line (after or inside a {pattern}
 * match).  A leading "0b" or "0B" is ignored.
 * One leading '-' is included in the number. 
 * 
 * With [x] sorting is done on the first hexadecimal
 * number in the line (after or inside a {pattern}
 * match).  A leading "0x" or "0X" is ignored.
 * One leading '-' is included in the number. 
 *
 * With [o] sorting is done on the first octal number in
 * the line (after or inside a {pattern} match).
 *  
 * With [u] only keep the first of a sequence of
 * identical lines (ignoring case when [i] is used).
 * Without this flag, a sequence of identical lines
 * will be kept in their original order.
 * Note that leading and trailing white space may cause
 * lines to be different.
 *
 * When /{pattern}/ is specified and there is no [r] flag
 * the text matched with {pattern} is skipped, so that
 * you sort on what comes after the match.
 * Instead of the slash any non-letter can be used.
 * For example, to sort on the second comma-separated
 * field:  
 * 			:sort /[^,]*,/ 
 * To sort on the text at virtual column 10 (thus
 * ignoring the difference between tabs and spaces):  
 * 			:sort /.*\%10v/
 * To sort on the first number in the line, no matter
 * what is in front of it:  
 * 			:sort /.\{-}\ze\d/
 * (Explanation: ".\{-}" matches any text, "\ze" sets the
 * end of the match and \d matches a digit.)
 * 
 * With [r] sorting is done on the matching {pattern} 
 * instead of skipping past it as described above.
 * For example, to sort on only the first three letters
 * of each line:  
 * 			:sort /\a\a\a/ r
 *  
 * If a {pattern} is used, any lines which don't have a
 * match for {pattern} are kept in their current order,
 * but separate from the lines which do match {pattern}.
 * If you sorted in reverse, they will be in reverse
 * order after the sorted lines, otherwise they will be
 * in their original order, right before the sorted
 * lines. 
 * 
 * If {pattern} is empty (e.g. // is specified), the
 * last search pattern is used.  This allows trying out
 * a pattern first. 
 * 
 * Note that using `:sort` with `:global` doesn't sort the 
 * matching lines, it's quite useless. 
 * 
 * The details about sorting depend on the library function used.
 * There is no guarantee that sorting is "stable" or obeys the 
 * current locale. You will have to try it out.
 *  
 * The sorting can be interrupted, but if you interrupt it too late in the
 * process you may end up with duplicated lines. This also depends on the system
 * library function used. 
 *
 * @author Brian Detweiler
 *
 */
public class SortCommand extends CountIgnoringNonRepeatableCommand {

	private static enum Options {
	    REVERSED,
	    NUMERIC,
	    IGNORE_CASE,
	    BINARY,
	    OCTAL,
	    HEX,
	    UNIQUE,
	    USE_PATTERN;
	}

	
	private static final String REVERSED_FLAG    = "!";
	private static final String NUMERIC_FLAG     = "n";
	private static final String IGNORE_CASE_FLAG = "i";
	private static final String BINARY_FLAG      = "b";
	private static final String OCTAL_FLAG       = "o";
	private static final String HEX_FLAG 		 = "x";
	private static final String UNIQUE_FLAG      = "u";
	
	/** String containing all the possible option flags */
	private static final String OPTIONS = REVERSED_FLAG 
										+ NUMERIC_FLAG
										+ IGNORE_CASE_FLAG
										+ BINARY_FLAG
										+ OCTAL_FLAG
										+ HEX_FLAG
										+ UNIQUE_FLAG;
	
	// Possible configurations for sort
	/** ! - reversed sort (entered as a modifier to :sort, as :sort! */
    private boolean reversed = false;
	/** n - numeric sort */
    private boolean numeric = false;
	/** i - ignore case */
    private boolean ignoreCase = false;
	/** b - binary sort */
    private boolean binary = false;
	/** x - hexadecimal sort */
    private boolean hex = false;
	/** o - octal sort */
    private boolean octal = false;
	/** u - works like sort -u on the command line - removes duplicate entries and sorts */
    private boolean unique = false;
	/** /regex pattern/ */
    private boolean usePattern = false;
    
    /**
     * sort takes an optional argument of "n" for Numeric sort.
     * @param option
     * @throws CommandExecutionException
     */
    public SortCommand(String[] options) throws CommandExecutionException {
        super();
       
        for(String option : options) {
        	if(option == null || option.trim().isEmpty())
        		continue;
        	else if(encodeOption(option) == Options.REVERSED)
        		reversed = true;
        	else if(encodeOption(option) == Options.NUMERIC)
        		numeric = true;
        	else if(encodeOption(option) == Options.IGNORE_CASE)
        		ignoreCase = true;
        	else if(encodeOption(option) == Options.BINARY)
        		binary = true;
        	else if(encodeOption(option) == Options.OCTAL)
        		octal = true;
        	else if(encodeOption(option) == Options.HEX)
        		hex = true;
        	else if(encodeOption(option) == Options.UNIQUE)
        		unique = true;
	        else
	        	throw new CommandExecutionException("Invalid argument: " + option);
        }
       
       
        // SANITY CHECKS
        // decimal (numeric), binary, hex, octal. Pick one. Or pick none. 
        if((!numeric && !binary && !hex && !octal) || (numeric ^ binary ^ hex ^ octal));
    	else
        	throw new CommandExecutionException("Invalid argument: " + options);
    
        /* XXX: I should mention, adding "i" to a numeric sort of any type will do nothing.
         *      But Vim doesn't throw an error, so we won't either. 
         *      Of note, Vim does not do a secondary sort. That is, if you were to sort 
         *      numerically on the following:
         *          1b
         *          2c
         *          1a
         *    	it would be sorted in Vim as follows:
         *    		1b
         *    		1a
         *    		2c
         *      Would it be useful to have the secondary ASCII sort, or would this break 
         *      expected functionality? Leaving this up for debate. -- BRD
         */  
    }
    
    private Options encodeOption(String option) {
    	if(option == null || "".equalsIgnoreCase(option) || !OPTIONS.contains(option))
    		return null;
    
    	if(option.equalsIgnoreCase(REVERSED_FLAG))
    		return Options.REVERSED;
    	else if(option.equalsIgnoreCase(NUMERIC_FLAG))
    		return Options.NUMERIC;
    	else if(option.equalsIgnoreCase(IGNORE_CASE_FLAG))
    		return Options.IGNORE_CASE;
    	else if(option.equalsIgnoreCase(OCTAL_FLAG))
    		return Options.OCTAL;
    	else if(option.equalsIgnoreCase(HEX_FLAG))
    		return Options.HEX;
    	else if(option.equalsIgnoreCase(UNIQUE_FLAG))
    		return Options.UNIQUE;
    	
    	return null;
    }
    
    @SuppressWarnings("unused")
	private String decodeOption(Options option) {
    	if(option == null)
    		return null;
   
    	switch(option) {
    		case REVERSED:
    			return REVERSED_FLAG;
    		case NUMERIC:
    			return NUMERIC_FLAG;
    		case IGNORE_CASE:
    			return IGNORE_CASE_FLAG;
    		case BINARY:
    			return BINARY_FLAG;
    		case OCTAL:
    			return OCTAL_FLAG;
    		case HEX:
    			return HEX_FLAG;
    		case UNIQUE:
    			return UNIQUE_FLAG;
    		default:
    			return null;
    	}
    }
   
    /**
     * According to Vim behavior, sorting by number will look at anything
     * BEGINNING with a number and sort accordingly
     * The following return true:
     * 		1
     * 		9L
     * 		67 Chevy
     * 		-29
     * 		blah blah 5 blah blah
     * 		0b01010
     * 		01234567123
     * 		Ox123
	 * NOTE: These will match to the very FIRST occurrence of a number
	 * 		 in their set. If it finds one, then we return true.
     * 		
     * @param str
     * @param base
     * @return
     */
    private boolean hasNumber(String str) {
    	if(binary)
    		return str.contains("0") || str.contains("1");
    	else if(octal)
    		return str.contains("0") ||
    			   str.contains("1") ||
    			   str.contains("2") ||
    			   str.contains("3") ||
    			   str.contains("4") ||
    			   str.contains("5") ||
    			   str.contains("6") ||
    			   str.contains("7");
    	else if(hex)
    		return str.contains("0") ||
    			   str.contains("1") ||
    			   str.contains("2") ||
    			   str.contains("3") ||
    			   str.contains("4") ||
    			   str.contains("5") ||
    			   str.contains("6") ||
    			   str.contains("7") ||
    			   str.contains("8") ||
    			   str.contains("9") ||
    			   str.contains("A") ||
    			   str.contains("a") ||
    			   str.contains("B") ||
    			   str.contains("b") ||
    			   str.contains("C") ||
    			   str.contains("c") ||
    			   str.contains("D") ||
    			   str.contains("d") ||
    			   str.contains("E") ||
    			   str.contains("e") ||
    			   str.contains("F") ||
    			   str.contains("f");
    	else
    		return str.contains("0") ||
    			   str.contains("1") ||
    			   str.contains("2") ||
    			   str.contains("3") ||
    			   str.contains("4") ||
    			   str.contains("5") ||
    			   str.contains("6") ||
    			   str.contains("7") ||
    			   str.contains("8") ||
    			   str.contains("9");
    }
    
	public void execute(EditorAdaptor editorAdaptor) throws CommandExecutionException {
        int line = editorAdaptor.getViewContent().getLineInformationOfOffset(
                editorAdaptor.getPosition().getViewOffset()).getNumber();
        try {
			doIt(editorAdaptor, line);
		} catch (Exception e) {
			throw new CommandExecutionException("sort failed: " + e.getMessage());
		}
	}

    public void doIt(EditorAdaptor editorAdaptor, int line) throws Exception {
  
    	SimpleConfiguration config = new SimpleConfiguration();
    	String nl = config.getNewLine();
    	
    	int length = editorAdaptor.getModelContent().getTextLength();
    	String editorContent = editorAdaptor.getModelContent().getText(0, length);
   
    	char[] editorContentArr = editorContent.toCharArray();
  
    	List<String> editorContentList = new ArrayList<String>();
    	String s = "";
    	for(char c : editorContentArr) {
    		s += c;
    		if(nl.equalsIgnoreCase(c + "")) {
    			editorContentList.add(s);
    			s = "";
    		}
    	}
    
    	// If the last line is a new line, we need to explicitly add that
    	if(nl.equalsIgnoreCase(editorContentArr[editorContentArr.length - 1] + ""))
			editorContentList.add(nl);
    	// Otherwise, we can just add the last line
    	else
			editorContentList.add(s + nl);
    	
    	if(numeric || binary || octal || hex) {
    		NumericStringComparator nsc = null;
    		if(binary)
    			nsc = new NumericStringComparator(BINARY_FLAG);
    		else if(octal)
    			nsc = new NumericStringComparator(OCTAL_FLAG);
    		else if(hex)
    			nsc = new NumericStringComparator(HEX_FLAG);
    		else if(numeric)
    			nsc = new NumericStringComparator(NUMERIC_FLAG);
    
    		List<String> numericList = new ArrayList<String>();
    		List<String> nonNumericList = new ArrayList<String>();
    		
    		for(String candidate : editorContentList) {
    			if(hasNumber(candidate))
    				numericList.add(candidate);
    			else
    				nonNumericList.add(candidate);
    		}
    		
    		Collections.sort(numericList, nsc);
    		editorContentList = new ArrayList<String>(nonNumericList);
    		editorContentList.addAll(numericList);
    	} else if(ignoreCase) {
    		IgnoreCaseStringComparator icsc = new IgnoreCaseStringComparator();
    		Collections.sort(editorContentList, icsc);
    	} else
    		Collections.sort(editorContentList);

    	// Little trick to get uniques from an ArrayList
    	// TODO: test to make sure they stay sorted
    	if(unique)
    		editorContentList = new ArrayList<String>(new HashSet<String>(editorContentList));
    	
    	if(reversed)
    		Collections.reverse(editorContentList);
    	
    	int size = editorContentList.size();
    	int count = 0;
    	String replacementText = "";
    	for(String editorLine : editorContentList) {
    		++count;
    		if(count == size && editorLine.endsWith(nl))
    			editorLine = editorLine.substring(0, editorLine.length() - 1);
    		replacementText += editorLine;
    	}
    	
    	// Replace the contents of the editor with the freshly sorted text
		editorAdaptor.getModelContent().replace(0, length, replacementText);
    }
    
    public boolean isNumeric() {
    	return numeric;
    }
    
    public void setNumeric(boolean numeric) {
    	this.numeric = numeric;
    }

	public boolean isReversed() {
		return reversed;
	}

	public void setReversed(boolean reversed) {
		this.reversed = reversed;
	}

	public boolean isIgnoreCase() {
		return ignoreCase;
	}

	public void setIgnoreCase(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
	}

	public boolean isHex() {
		return hex;
	}

	public void setHex(boolean hex) {
		this.hex = hex;
	}

	public boolean isOctal() {
		return octal;
	}

	public void setOctal(boolean octal) {
		this.octal = octal;
	}

	public boolean isUnique() {
		return unique;
	}

	public void setUnique(boolean unique) {
		this.unique = unique;
	}

	public boolean isUsePattern() {
		return usePattern;
	}

	public void setUsePattern(boolean usePattern) {
		this.usePattern = usePattern;
	}
}