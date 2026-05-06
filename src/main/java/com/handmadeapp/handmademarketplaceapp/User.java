package com.handmadeapp.handmademarketplaceapp;

/**
 * 
 * @author haryad
 */

public class User {
    private int userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String role;

    public User(int userId, String username, String email,
                String firstName, String lastName, String role) {
        this.userId    = userId;
        this.username  = username;
        this.email     = email;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.role      = role;
    }

    public int    getUserId()    { return userId; }
    public String getUsername()  { return username; }
    public String getEmail()     { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName()  { return lastName; }
    public String getRole()      { return role; }
    public String getFullName()  { return firstName + " " + lastName; }
}