package com.talkcare.models;

public class User {
    private int id;
    private String username;
    private String fullName;
    private String email;
    private String password;
    private String contactNo;
    private String address;
    private String role;
    private String profilePictureUrl;
    private String idType;
    private String idNumber;
    private String idDocumentUrl;
    private String status;

    public User() {}
 
    public User(int id, String username, String fullName, String email, String password, String contactNo, String address, String role, String profilePictureUrl, String idType, String idNumber, String idDocumentUrl, String status) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.contactNo = contactNo;
        this.address = address;
        this.role = role;
        this.profilePictureUrl = profilePictureUrl;
        this.idType = idType;
        this.idNumber = idNumber;
        this.idDocumentUrl = idDocumentUrl;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getContactNo() { return contactNo; }
    public void setContactNo(String contactNo) { this.contactNo = contactNo; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public String getIdDocumentUrl() { return idDocumentUrl; }
    public void setIdDocumentUrl(String idDocumentUrl) { this.idDocumentUrl = idDocumentUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

