package com.talkcare.models;

import java.sql.Timestamp;

public class Report {
    private int id;
    private String reportCode;
    private Integer userId;
    private String fullName;
    private String email;
    private String address;
    private String contactNo;
    private String concernType;
    private String involvedWitness;
    private String concernDetails;
    private String status;
    private Timestamp createdAt;

    public Report() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getReportCode() { return reportCode; }
    public void setReportCode(String reportCode) { this.reportCode = reportCode; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactNo() { return contactNo; }
    public void setContactNo(String contactNo) { this.contactNo = contactNo; }

    public String getConcernType() { return concernType; }
    public void setConcernType(String concernType) { this.concernType = concernType; }

    public String getInvolvedWitness() { return involvedWitness; }
    public void setInvolvedWitness(String involvedWitness) { this.involvedWitness = involvedWitness; }

    public String getConcernDetails() { return concernDetails; }
    public void setConcernDetails(String concernDetails) { this.concernDetails = concernDetails; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

