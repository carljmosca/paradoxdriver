package com.googlecode.paradox.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;

import com.googlecode.paradox.ParadoxConnection;
import com.googlecode.paradox.data.table.value.FieldValue;
import com.googlecode.paradox.metadata.ParadoxField;
import com.googlecode.paradox.metadata.ParadoxTable;
import com.googlecode.paradox.utils.Constants;
import com.googlecode.paradox.utils.DateUtils;
import com.googlecode.paradox.utils.filefilters.TableFilter;
import java.math.BigInteger;
import java.text.DecimalFormat;

public class TableData {

    public static ArrayList<ParadoxTable> listTables(final ParadoxConnection conn, final String pattern) throws SQLException {
        final ArrayList<ParadoxTable> tables = new ArrayList<ParadoxTable>();
        final File[] fileList = conn.getDir().listFiles(new TableFilter(pattern));
        for (final File file : fileList) {
            try {
                if (!file.getAbsolutePath().toLowerCase().endsWith(".db")) {
                    continue;
                }
                final ParadoxTable table = TableData.loadTableHeader(file);
                if (table.isValid()) {
                    tables.add(table);
                }
            } catch (final IOException ex) {
                throw new SQLException("Error loading Paradox tables.", ex);
            }
        }
        return tables;
    }

    public static ArrayList<ParadoxTable> listTables(final ParadoxConnection conn) throws SQLException {
        final ArrayList<ParadoxTable> tables = new ArrayList<ParadoxTable>();
        final File[] fileList = conn.getDir().listFiles(new TableFilter());
        for (final File file : fileList) {
            try {
                if (!file.getAbsolutePath().toLowerCase().endsWith(".db")) {
                    continue;
                }
                final ParadoxTable table = TableData.loadTableHeader(file);
                if (table.isValid()) {
                    tables.add(table);
                }
            } catch (final IOException ex) {
                throw new SQLException("Error loading Paradox tables.", ex);
            }
        }
        return tables;
    }

    public static ArrayList<ArrayList<FieldValue>> loadData(final ParadoxConnection conn, final ParadoxTable table, final Collection<ParadoxField> fields) throws IOException, SQLException {
        final ArrayList<ArrayList<FieldValue>> ret = new ArrayList<ArrayList<FieldValue>>();
        final FileInputStream fs = new FileInputStream(table.getFile());
        final int blockSize = table.getBlockSizeBytes();
        final int recordSize = table.getRecordSize();
        final int headerSize = table.getHeaderSize();
        final ByteBuffer buffer = ByteBuffer.allocate(blockSize);
        final ByteBuffer valueString = ByteBuffer.allocate(Constants.MAX_STRING_SIZE);
        FileChannel channel = null;

        try {
            channel = fs.getChannel();

            if (table.getUsedBlocks() > 0) {
                int nextBlock = table.getFirstBlock();
                do {
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    channel.position(headerSize + (nextBlock - 1) * blockSize);

                    buffer.clear();
                    channel.read(buffer);
                    buffer.flip();

                    nextBlock = buffer.getShort();
                    // final int blockNumber = buffer.getShort();;
                    buffer.getShort();

                    final int addDataSize = buffer.getShort() & 0xFFFF;
                    final int rowsInBlock = addDataSize / recordSize + 1;

                    buffer.order(ByteOrder.BIG_ENDIAN);

                    for (int loop = 0; loop < rowsInBlock; loop++) {
                        final ArrayList<FieldValue> row = new ArrayList<FieldValue>();

                        for (final ParadoxField field : table.getFields()) {
                            FieldValue fieldValue = null;

                            switch (field.getType()) {
                                case 1: {
                                    // VARCHAR type

                                    // reset buffer to zeros
                                    valueString.clear();
                                    Arrays.fill(valueString.array(), (byte) 0);

                                    for (int chars = 0; chars < field.getSize(); chars++) {
                                        valueString.put(buffer.get());
                                    }
                                    fieldValue = new FieldValue(TableData.parseString(valueString, table.getCharset()), Types.VARCHAR);
                                    break;
                                }
                                case 2: {
                                    // DATE type
                                    final int a1 = 0x000000FF & buffer.get();
                                    final int a2 = 0x000000FF & buffer.get();
                                    final int a3 = 0x000000FF & buffer.get();
                                    final int a4 = 0x000000FF & buffer.get();
                                    final long days = (a1 << 24 | a2 << 16 | a3 << 8 | a4) & 0x0FFFFFFFL;

                                    if ((a1 & 0xB0) != 0) {
                                        final Date date = DateUtils.SdnToGregorian(days + 1721425);
                                        fieldValue = new FieldValue(date, Types.DATE);
                                    } else {
                                        fieldValue = new FieldValue(Types.DATE);
                                    }
                                    break;
                                }
                                case 3: {
                                    //PARADOX: Smallint (may be only 2 bytes)
                                    if(field.getSize()==2) {
                                        //v = buffer.getShort(); //pardox set high bit so always negative
                                        byte b1 = buffer.get();
                                        byte b2 = buffer.get();
                                        final int v=(b1&0x7f)*256 +b2;
                                        fieldValue = new FieldValue(v, Types.SMALLINT);
                                    } else {
                                        //final int v = buffer.getInt(); /pardox set high bit if number too large
                                    	byte[] b=new byte[4];
                                        for (int i = 0; i < b.length; ++i) {
                                        	b[i]=buffer.get();
                                        }
                                        b[0]&=0x7f;
                                    	BigInteger bi=new BigInteger(b);
                                        fieldValue = new FieldValue(bi.longValue(), Types.BIGINT);
                                    }
                                    break;
                                }
                                case 4: {
                                    // FIME long value
//                                    final int v = buffer.getInt();
//                                    fieldValue = new FieldValue(v, Types.BIGINT);
                                    //final int v = buffer.getInt(); /pardox set high bit if number too large
                                    byte[] b=new byte[4];
                                    for (int i = 0; i < b.length; ++i) {
                                            b[i]=buffer.get();
                                    }
                                    b[0]&=0x7f;
                                    BigInteger bi=new BigInteger(b);
                                        fieldValue = new FieldValue(bi.longValue(), Types.BIGINT);
                                    break;
                                }
                                case 5: 
                                    // Currency - format to currency below
                                case 6: {
                                    // Number
                                    final double v = buffer.getDouble() * -1;
                                    if (Double.compare(Double.NEGATIVE_INFINITY, 1 / v) == 0) {
                                        fieldValue = new FieldValue(Types.DOUBLE);
                                    } else {
                                        if(field.getType()==5) {
                                            fieldValue = new FieldValue(DecimalFormat.getCurrencyInstance().format(v), Types.DOUBLE);
                                        } else {
                                            fieldValue = new FieldValue(v, Types.DOUBLE);
                                        }
                                    }
                                    break;
                                }
                                case 9: {
                                    // Logical
                                    final byte v = buffer.get();
                                    switch (v) {
                                        case 0:
                                            fieldValue = new FieldValue(Types.BOOLEAN);
                                            break;
                                        case -127:
                                            fieldValue = new FieldValue(Boolean.TRUE, Types.BOOLEAN);
                                            break;
                                        case -128:
                                            fieldValue = new FieldValue(Boolean.TRUE, Types.BOOLEAN);
                                            break;
                                        default:
                                            throw new SQLException("Invalid value " + v + ".");
                                    }
                                    break;
                                }
                                case 0xc: {
                                    // FIXME blob type
                                    final ByteBuffer value = ByteBuffer.allocate(field.getSize());

                                    for (int chars = 0; chars < field.getSize(); chars++) {
                                        value.put(buffer.get());
                                    }
                                    value.flip();
                                    final String v = table.getCharset().decode(value).toString();
                                    fieldValue = new FieldValue(v, Types.BLOB);
                                    break;
                                }
                                case 0x14: {
                                    //TIME (4)
                                    final int a1 = 0x000000FF & buffer.get();
                                    final int a2 = 0x000000FF & buffer.get();
                                    final int a3 = 0x000000FF & buffer.get();
                                    final int a4 = 0x000000FF & buffer.get();
                                    final long timeInMillis = (a1 << 24 | a2 << 16 | a3 << 8 | a4) & 0x0FFFFFFFL;
                                        if ((a1 & 0xB0) != 0) {
                                                final Calendar calendar = new GregorianCalendar(1, 0, 0);
                                                calendar.add(Calendar.MILLISECOND, (int) timeInMillis);
                                                final Time time = new Time(calendar.getTimeInMillis());
                                                fieldValue = new FieldValue(time, Types.TIME);
                                        } else {
                                                fieldValue = new FieldValue(Types.TIME);
                                        }
                                        break;
                                }
                                case 0x16: {
                                        // Autoincrement
                                        final int v = buffer.getInt() & 0x0FFFFFFF;
                                        fieldValue = new FieldValue(v, Types.INTEGER);
                                        break;
                                }
                                case 0x15: { //TODO ptk check if this is correct - http://docwiki.embarcadero.com/RADStudio/Seattle/en/Internal_Data_Formats
                                        //TIMESTAMP
                                        double v = buffer.getDouble() * -1; //highbit set and 2's comp in paradox
                                        long lv=new Double(v).longValue();
                                        lv-=(719163L * 86400000L); //days from 1/1/1 to 1/1/1970 in milliseconds
                                        Calendar c = Calendar.getInstance();
                                        c.setTimeInMillis(lv);
                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");            
                                        fieldValue = new FieldValue(sdf.format(c.getTime()), Types.TIMESTAMP);
                                        break;
                                }
                                default:
                                        throw new SQLException("Type " + field.getType() + " not found.");
                                }
                                // Field filter
                                if (fields.contains(field)) {
                                        row.add(fieldValue);
                            }
                        }
                        ret.add(row);
                    }
		} while (nextBlock != 0);
			}
		} finally {
			if (channel != null) {
				channel.close();
			}
			fs.close();
		}
		return ret;
	}

    private static ParadoxTable loadTableHeader(final File file) throws IOException {
        final FileInputStream fs = new FileInputStream(file);
        final ParadoxTable table = new ParadoxTable(file, file.getName());
        ByteBuffer buffer = ByteBuffer.allocate(16192);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        FileChannel channel = null;

        try {
            channel = fs.getChannel();
            channel.read(buffer);
            buffer.flip();

            table.setRecordSize(buffer.getShort());
            table.setHeaderSize(buffer.getShort());
            table.setType(buffer.get());
            table.setBlockSize(buffer.get());
            table.setRowCount(buffer.getInt());
            table.setUsedBlocks(buffer.getShort());
            table.setTotalBlocks(buffer.getShort());
            table.setFirstBlock(buffer.getShort());
            table.setLastBlock(buffer.getShort());

            buffer.position(0x21);
            table.setFieldCount(buffer.getShort());
            table.setPrimaryFieldCount(buffer.getShort());

            buffer.position(0x38);
            table.setWriteProtected(buffer.get());
            table.setVersionId(buffer.get());

            buffer.position(0x49);
            table.setAutoIncrementValue(buffer.getInt());
            table.setFirstFreeBlock(buffer.getShort());

            buffer.position(0x55);
            table.setReferencialIntegrity(buffer.get());

            if (table.getVersionId() > 4) {
                // Set the charset
                buffer.position(0x6A);
//                table.setCharset(Charset.forName("cp" + buffer.getShort()));
                table.setCharset(Charset.forName("Cp1250"));

                buffer.position(0x78);
            } else {
                buffer.position(0x58);
            }

            final ArrayList<ParadoxField> fields = new ArrayList<ParadoxField>();
            for (int loop = 0; loop < table.getFieldCount(); loop++) {
                final ParadoxField field = new ParadoxField();
                field.setType(buffer.get());
                field.setSize((short) (buffer.get() & 0xff));
                field.setTableName(table.getName());
                field.setTable(table);
                fields.add(field);
            }

            // Restart the buffer with all table header
            channel.position(0);
            buffer = ByteBuffer.allocate(table.getHeaderSize());
            channel.read(buffer);

            if (table.getVersionId() > 4) {
                if (table.getVersionId() == 0xC) {
                    buffer.position(0x78 + 261 + 4 + 6 * fields.size());
                } else {
                    buffer.position(0x78 + 83 + 6 * fields.size());
                }
            } else {
                buffer.position(0x58 + 83 + 6 * fields.size());
            }

            for (int loop = 0; loop < table.getFieldCount(); loop++) {
                final ByteBuffer name = ByteBuffer.allocate(261);

                while (true) {
                    final byte c = buffer.get();
                    if (c == 0) {
                        break;
                    }
                    name.put(c);
                }
                name.flip();
                fields.get(loop).setName(table.getCharset().decode(name).toString());
            }
            table.setFields(fields);

            final ArrayList<Short> fieldsOrder = new ArrayList<Short>();
            for (int loop = 0; loop < table.getFieldCount(); loop++) {
                fieldsOrder.add(buffer.getShort());
            }
            table.setFieldsOrder(fieldsOrder);
        } finally {
            if (channel != null) {
                channel.close();
            }
            fs.close();
        }
        return table;
    }

    /**
     * Convert the Paradox VARCHAR to Java String.
     *
     * The paradox fill the entire buffer with zeros at end of VARCHAR literals
     *
     * @param buffer VARCHAR Buffer to convert
     * @param charset Table Charset (in Java Format)
     * @return a Java String
     */
    private static String parseString(final ByteBuffer buffer, final Charset charset) {
        final byte[] value = buffer.array();
        int length = value.length;
        for (; length > 0; length--) {
            // array value starts with zero, not 1
            if (value[length - 1] != 0) {
                break;
            }
        }
        buffer.flip();
        buffer.limit(length);
        return charset.decode(buffer).toString();
    }

    private TableData() {
    }
}
