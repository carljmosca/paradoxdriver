package com.googlecode.paradox;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;

import com.googlecode.paradox.data.TableData;
import com.googlecode.paradox.data.table.value.FieldValue;
import com.googlecode.paradox.metadata.ParadoxField;
import com.googlecode.paradox.metadata.ParadoxTable;
import com.googlecode.paradox.parser.SQLParser;
import com.googlecode.paradox.parser.nodes.SQLNode;
import com.googlecode.paradox.parser.nodes.SelectNode;
import com.googlecode.paradox.parser.nodes.StatementNode;
import com.googlecode.paradox.parser.nodes.TableNode;
import com.googlecode.paradox.planner.Planner;
import com.googlecode.paradox.planner.plan.SelectPlan;
import com.googlecode.paradox.results.Column;
import com.googlecode.paradox.utils.SQLStates;
import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.Collection;

/**
 * Statement para o PARADOX
 *
 * @author Leonardo Alves da Costa
 * @version 1.0
 * @since 15/03/2009
 */
public class ParadoxStatement implements Statement {

	private final ParadoxConnection conn;
	private boolean closed = false;
	private int maxFieldSize = 255;
	private int maxRows = 0;
	private SQLWarning warnings = null;
	private boolean poolable = false;
	private int fetchSize = 10;
	private ParadoxResultSet rs = null;
	private int fetchDirection = ResultSet.FETCH_FORWARD;
	private int queryTimeout = 20;
	String cursorName = "NO_NAME";

	public ParadoxStatement(final ParadoxConnection conn) {
		this.conn = conn;
	}

	@Override
	public ResultSet executeQuery(final String sql) throws SQLException {
		if (rs != null && !rs.isClosed()) {
			rs.close();
		}
		final SQLParser parser = new SQLParser(sql);
		final ArrayList<StatementNode> statementList = parser.parse();
		if (statementList.size() > 1) {
			throw new SQLFeatureNotSupportedException("Unsupported operation.", SQLStates.INVALID_SQL);
		}
		final StatementNode node = statementList.get(0);
		if (!(node instanceof SelectNode)) {
			throw new SQLFeatureNotSupportedException("Not a SELECT statement.", SQLStates.INVALID_SQL);
		}
		try {
			executeSelect((SelectNode) node);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return rs;
	}

	@Override
	public int executeUpdate(final String sql) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void close() throws SQLException {
		if (rs != null && !rs.isClosed()) {
			rs.close();
		}
		closed = true;
	}

	@Override
	public int getMaxFieldSize() throws SQLException {
		return maxFieldSize;
	}

	@Override
	public void setMaxFieldSize(final int max) throws SQLException {
		if (max > 255) {
			throw new SQLException("Value bigger than 255.", SQLStates.INVALID_PARAMETER);
		}
		maxFieldSize = max;
	}

	@Override
	public int getMaxRows() throws SQLException {
		return maxRows;
	}

	@Override
	public void setMaxRows(final int max) throws SQLException {
		maxRows = max;
	}

	@Override
	public void setEscapeProcessing(final boolean enable) throws SQLException {
	}

	@Override
	public int getQueryTimeout() throws SQLException {
		return queryTimeout;
	}

	@Override
	public void setQueryTimeout(final int seconds) throws SQLException {
		queryTimeout = seconds;
	}

	@Override
	public void cancel() throws SQLException {
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return warnings;
	}

	@Override
	public void clearWarnings() throws SQLException {
		warnings = null;
	}

	@Override
	public void setCursorName(final String name) throws SQLException {
		cursorName = name;
	}

	@Override
	public boolean execute(final String sql) throws SQLException {
		if (rs != null && !rs.isClosed()) {
			rs.close();
		}
		boolean select = false;
		final SQLParser parser = new SQLParser(sql);
		final ArrayList<StatementNode> statements = parser.parse();
		for (final StatementNode statement : statements) {
			if (statement instanceof SelectNode) {
				try {
					executeSelect((SelectNode) statement);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				select = true;
			}
		}
		return select;
	}

        private byte getColTypeFromColName(String colName) throws SQLException {
        	if(colName.equals("VARCHAR")) return Types.VARCHAR;
        	else if(colName.equals("DATE")) return Types.DATE;
        	else if(colName.equals("INTEGER")) return Types.INTEGER;
        	else if(colName.equals("DOUBLE")) return Types.DOUBLE;
        	else if(colName.equals("NUMERIC")) return Types.NUMERIC;
        	else if(colName.equals("BOOLEAN")) return Types.BOOLEAN;
        	else if(colName.equals("BLOB")) return (byte) Types.BLOB;
        	else if(colName.equals("TIME")) return Types.TIME;
        	else if(colName.equals("TIMESTAMP")) return Types.TIMESTAMP;
        	else if(colName.equals("BINARY")) return Types.BINARY;
            else
                throw new SQLException("Type Unknown", SQLStates.TYPE_NOT_FOUND);
        }
        
        private Collection<ParadoxField> getAllColumns(String tableName, ArrayList<String> columnsNeeded)  throws Exception {
 		ResultSet rs = null;
                Collection<ParadoxField> retColumns=new ArrayList<ParadoxField>();
                boolean getAll=columnsNeeded.contains("*");
		try {
			final DatabaseMetaData meta = conn.getMetaData();

			rs = meta.getColumns(null, null, tableName, "%");
			while (rs.next()) {
                            if(getAll || columnsNeeded.contains(rs.getString("COLUMN_NAME"))) {
                                ParadoxField column=new ParadoxField();
                                column.setName(rs.getString("COLUMN_NAME"));
                                column.setType((byte)(Integer.parseInt(rs.getString("DATA_TYPE"))%256)); //getColTypeFromName(rs.getString("DATA_TYPE")));
                                column.setSize((short)Integer.parseInt(rs.getString("COLUMN_SIZE")));
                                retColumns.add(column);
                            }
			}
		} finally {
			if (rs != null) {
				rs.close();
			}
		}
                return retColumns;
       }
	private void executeSelect(final SelectNode node) throws Exception {
            final Planner planner = new Planner(conn);
            final SelectPlan plan = (SelectPlan) planner.create(node);

            //tabe name
            if(node.getTables().size()!=1)
                throw new SQLException("Cannot handle query with more than one table.");
            String tableName=node.getTables().get(0).getAlias();
            if(tableName!=null && !tableName.isEmpty()) {
                //columns
                ArrayList<String> columnsNeeded = new ArrayList<String>();
                if(node.getFields().size()<1)
                    throw new SQLException("No vailid field names.");
                if("*".equals(node.getFields().get(0).getAlias())) {
                    //all fields
                    columnsNeeded.add("*");
                } else {
                   //specific fields
                    for (SQLNode sqlNode : node.getFields()) {
                        columnsNeeded.add(sqlNode.getAlias());
                    }
                }
                Collection<ParadoxField> pColumns=getAllColumns(tableName,columnsNeeded);
                //now the rows
                ArrayList<ParadoxTable> pTables=TableData.listTables(conn);
                ParadoxTable pTable=null;
                for(ParadoxTable pT: pTables) {
                    for(ParadoxField pF:pT.getFields()) {
                        if(tableName.equalsIgnoreCase(pF.getTableName())) {
                            pTable=pT;
                            break;
                        }
                    }
                    if(pTable!=null) break;
                }
                final ArrayList<ArrayList<FieldValue>> values = TableData.loadData(conn, pTable, pColumns);
                final ArrayList<Column> columns= new ArrayList<Column>();
                for(ParadoxField p:pColumns) {
                	columns.add(new Column(p));
                }
                plan.execute();
                rs = new ParadoxResultSet(conn, this, values, columns);
            } else {
                throw new SQLException("Unable to find a valid table name.");
            }
 	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return rs;
	}

	@Override
	public int getUpdateCount() throws SQLException {
		return -1;
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		return false;
	}

	@Override
	public void setFetchDirection(final int direction) throws SQLException {
		if (direction != ResultSet.FETCH_FORWARD) {
			throw new SQLException("O resultset somente pode ser ResultSet.FETCH_FORWARD", SQLStates.INVALID_PARAMETER);
		}
		fetchDirection = direction;
	}

	@Override
	public int getFetchDirection() throws SQLException {
		return fetchDirection;
	}

	@Override
	public void setFetchSize(final int rows) throws SQLException {
		fetchSize = rows;
	}

	@Override
	public int getFetchSize() throws SQLException {
		return fetchSize;
	}

	@Override
	public int getResultSetConcurrency() throws SQLException {
		return ResultSet.CONCUR_READ_ONLY;
	}

	@Override
	public int getResultSetType() throws SQLException {
		return ResultSet.TYPE_FORWARD_ONLY;
	}

	@Override
	public void addBatch(final String sql) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void clearBatch() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int[] executeBatch() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Connection getConnection() throws SQLException {
		return conn;
	}

	@Override
	public boolean getMoreResults(final int current) throws SQLException {
		return false;
	}

	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		return new ParadoxResultSet(conn, this, new ArrayList<ArrayList<FieldValue>>(), new ArrayList<Column>());
	}

	@Override
	public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
		return 0;
	}

	@Override
	public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
		return 0;
	}

	@Override
	public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
		return 0;
	}

	@Override
	public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
		return execute(sql);
	}

	@Override
	public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
		return execute(sql);
	}

	@Override
	public boolean execute(final String sql, final String[] columnNames) throws SQLException {
		return execute(sql);
	}

	@Override
	public int getResultSetHoldability() throws SQLException {
		return conn.getHoldability();
	}

	@Override
	public boolean isClosed() throws SQLException {
		return closed;
	}

	@Override
	public void setPoolable(final boolean poolable) throws SQLException {
		this.poolable = poolable;
	}

	@Override
	public boolean isPoolable() throws SQLException {
		return poolable;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T unwrap(final Class<T> iface) throws SQLException {
		if (isWrapperFor(iface)) {
			return (T) this;
		}
		throw new SQLException("Type not found.", SQLStates.TYPE_NOT_FOUND);
	}

	@Override
	public boolean isWrapperFor(final Class<?> iface) throws SQLException {
		return getClass().isAssignableFrom(iface);
	}

	public void closeOnCompletion() throws SQLException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean isCloseOnCompletion() throws SQLException {
		return true;
	}
}
