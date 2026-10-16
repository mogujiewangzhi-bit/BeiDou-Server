package org.gms.service;

import org.gms.util.I18nUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class AdminRewardReceipt {
    private AdminRewardReceipt() {}

    public static void markDelivered(Connection con, int characterId, List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE admin_resource_grants SET status='delivered', delivered_at=CURRENT_TIMESTAMP WHERE id=? AND character_id=? AND status='pending'")) {
            for (long id : ids) {
                ps.setLong(1, id);
                ps.setInt(2, characterId);
                if (ps.executeUpdate() != 1) throw new SQLException(I18nUtil.getExceptionMessage("Grant.receiptConflict"));
            }
        }
    }
}
