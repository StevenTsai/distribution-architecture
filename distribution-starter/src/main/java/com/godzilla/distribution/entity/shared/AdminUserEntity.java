package com.godzilla.distribution.entity.shared;

import java.util.Date;

public class AdminUserEntity {
    private Long id;
    private String username;
    private String password;
    private Byte status;
    private Long userId;
    private Date createTime;
    private Date modifyTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getModifyTime() { return modifyTime; }
    public void setModifyTime(Date modifyTime) { this.modifyTime = modifyTime; }
}
