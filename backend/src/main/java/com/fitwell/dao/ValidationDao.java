package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.service.sourcing.model.ValidationItemRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ValidationDao {

    private final ConnectionPool pool;

    public ValidationDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public List<ValidationItemRecord> getPending() throws SQLException {
        String sql = """
            SELECT v.id, v.source_id, s.name AS source_name, v.raw_page_text,
                   v.extracted_fields_json, v.confidence_score, v.duplicate_of_offer_id,
                   v.status, v.created_at
            FROM validation_items v
            JOIN sources s ON v.source_id = s.id
            WHERE v.status = 'PENDING'
            ORDER BY v.created_at DESC
        """;

        List<ValidationItemRecord> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new ValidationItemRecord(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("source_id"),
                        rs.getString("source_name"),
                        rs.getString("raw_page_text"),
                        rs.getString("extracted_fields_json"),
                        rs.getInt("confidence_score"),
                        (UUID) rs.getObject("duplicate_of_offer_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
                ));
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }

    public UUID addItem(UUID sourceId, String rawPageText, String extractedFieldsJson, int confidenceScore, UUID duplicateOfOfferId) throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = """
            INSERT INTO validation_items (id, source_id, raw_page_text, extracted_fields_json, confidence_score, duplicate_of_offer_id, status, created_at)
            VALUES (?, ?, ?, ?::jsonb, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
        """;

        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, sourceId);
            ps.setString(3, rawPageText != null ? rawPageText : "");
            ps.setString(4, extractedFieldsJson != null ? extractedFieldsJson : "{}");
            ps.setInt(5, confidenceScore);
            ps.setObject(6, duplicateOfOfferId);
            ps.executeUpdate();
            return id;
        } finally {
            pool.release(conn);
        }
    }

    public void updateStatus(UUID id, String status) throws SQLException {
        String sql = "UPDATE validation_items SET status = ? WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }
}
