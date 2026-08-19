package com.brainhealth.search.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "saved_search")
public class SavedSearch extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "query_json", columnDefinition = "TEXT")
    private String queryJson;
    @Column(name = "user_id")
    private Long userId;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getQueryJson() { return queryJson; }
    public void setQueryJson(String v) { queryJson = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { userId = v; }
}
