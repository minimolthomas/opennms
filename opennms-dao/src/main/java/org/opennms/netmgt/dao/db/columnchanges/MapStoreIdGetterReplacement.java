
/**
 * <p>MapStoreIdGetterReplacement class.</p>
 *
 * @author ranger
 * @version $Id: $
 */
package org.opennms.netmgt.dao.db.columnchanges;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.opennms.netmgt.dao.db.ColumnChange;
import org.opennms.netmgt.dao.db.ColumnChangeReplacement;
public class MapStoreIdGetterReplacement implements ColumnChangeReplacement {
    private final AutoIntegerIdMapStoreReplacement m_storeFoo;
    private final String[] m_indexColumns;
    private final boolean m_noMatchOkay;
    
    /**
     * <p>Constructor for MapStoreIdGetterReplacement.</p>
     *
     * @param storeFoo a {@link org.opennms.netmgt.dao.db.columnchanges.AutoIntegerIdMapStoreReplacement} object.
     * @param columns an array of {@link java.lang.String} objects.
     * @param noMatchOkay a boolean.
     */
    public MapStoreIdGetterReplacement(AutoIntegerIdMapStoreReplacement storeFoo,
            String[] columns, boolean noMatchOkay) {
        m_storeFoo = storeFoo;
        m_indexColumns = columns;
        m_noMatchOkay = noMatchOkay;
    }

    /** {@inheritDoc} */
    public Object getColumnReplacement(ResultSet rs, Map<String, ColumnChange> columnChanges) throws SQLException {
        return m_storeFoo.getIntegerForColumns(rs, columnChanges, m_indexColumns, m_noMatchOkay);
    }
    
    /**
     * <p>addColumnIfColumnIsNew</p>
     *
     * @return a boolean.
     */
    public boolean addColumnIfColumnIsNew() {
        return true;
    }
    
    /**
     * <p>close</p>
     */
    public void close() {
    }
}