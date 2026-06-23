package com.godzilla.distribution.entity.shared;

import java.util.Date;

public class UserLoginSessionEntity {
    private Long id;
    private String openid;
    private String skey;
    private String biz;
    private String loginSource;
    private Date expireTime;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getSkey() { return skey; }
    public void setSkey(String skey) { this.skey = skey; }
    public String getBiz() { return biz; }
    public void setBiz(String biz) { this.biz = biz; }
    public String getLoginSource() { return loginSource; }
    public void setLoginSource(String loginSource) { this.loginSource = loginSource; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
