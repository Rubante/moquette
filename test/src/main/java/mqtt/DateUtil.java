package mqtt;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {

    /**
     * Parses text in 'YYYY-MM-DD' format to
     * produce a date.
     *
     * @param s the text
     * @return Date
     */
    static public Date parseDateTime(String s) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return format.parse(s);
        } catch (Exception ex) {
            return null;
        }
    }

    static public String getNowDateTimeStr() {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return format.format(new Date());
        } catch (Exception ex) {
            return "";
        }
    }

    static public int diffDateD(Date sd, Date ed) throws ParseException {
        return Math.round((ed.getTime() - sd.getTime()) / 86400000) + 1;
    }
}
