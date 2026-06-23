package com.godzilla.distribution.entity.shared;

import java.util.Date;

public class MedicalUserInfoEntity {
    private Long id;
    private String openid;
    private String name;
    private String avatar;
    private String gender;
    private String country;
    private String province;
    private String city;
    private String language;
    private Date createTime;
    private Date modifyTime;
    private String phonenumber;
    private Byte role;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getModifyTime() { return modifyTime; }
    public void setModifyTime(Date modifyTime) { this.modifyTime = modifyTime; }
    public String getPhonenumber() { return phonenumber; }
    public void setPhonenumber(String phonenumber) { this.phonenumber = phonenumber; }
    public Byte getRole() { return role; }
    public void setRole(Byte role) { this.role = role; }
}
