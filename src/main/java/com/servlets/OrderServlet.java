package com.servlets;


import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/OrderServlet")
public class OrderServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    public OrderServlet() {}
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //default redirect to itself preventing unwanted get actions performed by user which can lead to malfunctions
        request.getRequestDispatcher("/index.jsp?page=cart").forward(request, response);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();

        String username = session.getAttribute("username").toString();

        double totalPrice = (double) session.getAttribute("totalPrice");
        double userBalance = (double) session.getAttribute("balance");
        totalPrice = Double.parseDouble(String.format("%.2f", totalPrice));
        userBalance = Double.parseDouble(String.format("%.2f", userBalance));

        ArrayList<Integer> bookIDs = (ArrayList<Integer>) session.getAttribute("bookCart");
        ArrayList<Integer> accessoryIDs = (ArrayList<Integer>) session.getAttribute("accessoriesCart");

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            request.setAttribute("messageOrder", "Transaction failed. Please try again.");
            throw new RuntimeException(e);
        }

        // get ID of a user that is making a transaction
        int userId = -1;
        try {
            userId = getUserId(username, getServletContext());
        } catch (ClassNotFoundException | SQLException e) {
            request.setAttribute("messageOrder", "Transaction failed. Please try again.");
            throw new RuntimeException(e);

        }

        if(userId != -1) {
            if(userBalance < totalPrice) {
                //check if user has enough money to buy items in cart
                request.setAttribute("messageOrder", "Transaction failed. Insufficient funds.");
                request.getRequestDispatcher("index.jsp?page=cart").forward(request, response);
            } else {
                try {
                    //update all data in database
                    addOrder(username, getServletContext());
                    addOrderDetails(getLastOrderId(username, getServletContext()), bookIDs, accessoryIDs, getServletContext());
                    updateBooksQuantity(bookIDs, getServletContext());
                    updateAccessoriesQuantity(accessoryIDs, getServletContext());
                    updateUserBalance(userId, totalPrice, getServletContext(), session);
                } catch (ClassNotFoundException | SQLException e) {
                    request.setAttribute("messageOrder", "Transaction failed. Please try again.");
                    throw new RuntimeException(e);
                }
            }
        }

        request.setAttribute("messageOrder", "Transaction completed. Thank you for your order!");
        //remove items from cart by reseting both attributes
        session.setAttribute("bookCart", null);
        session.setAttribute("accessoriesCart", null);
        //send the message to the page
        request.getRequestDispatcher("index.jsp?page=cart").forward(request, response);

    }
    protected int getUserId(String username, ServletContext context) throws ClassNotFoundException, SQLException {
        int id = -1;
        String path = context.getRealPath("/WEB-INF/database.db");
        String sql = "SELECT UserID FROM Users WHERE Username = ?";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    id = rs.getInt("UserID");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return id;
    }
    protected void addOrder(String username, ServletContext context) throws ClassNotFoundException, SQLException {
        String path = context.getRealPath("/WEB-INF/database.db");
        String sql = "INSERT INTO Orders (Username, OrderDate) VALUES (?, ?);";
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, currentDate);

            pstmt.executeUpdate();
        }
    }

    protected int getLastOrderId(String username, ServletContext context) throws ClassNotFoundException, SQLException {
        int orderId = -1;
        String path = context.getRealPath("/WEB-INF/database.db");
        String sql = "SELECT OrderID FROM Orders WHERE Username = ? ORDER BY OrderID DESC LIMIT 1";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    orderId = rs.getInt("OrderID");
                }
            }
        }
        return orderId;
    }

    protected void addOrderDetails(int orderId, ArrayList<Integer> bookIDs, ArrayList<Integer> accessoryIDs, ServletContext context) throws ClassNotFoundException, SQLException {
        String path = context.getRealPath("/WEB-INF/database.db");

        String sql = "INSERT INTO OrderItems (OrderID, ItemType, ItemID, Quantity) VALUES (?, ?, ?, ?);";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            Map<Integer, Integer> bookCount = new HashMap<>();
            Map<Integer, Integer> accessoryCount = new HashMap<>();

            for (Integer bookId : bookIDs) {
                bookCount.put(bookId, bookCount.getOrDefault(bookId, 0) + 1);
            }

            for (Integer accessoryId : accessoryIDs) {
                accessoryCount.put(accessoryId, accessoryCount.getOrDefault(accessoryId, 0) + 1);
            }

            for (Map.Entry<Integer, Integer> entry : bookCount.entrySet()) {
                stmt.setInt(1, orderId);
                stmt.setString(2, "book");
                stmt.setInt(3, entry.getKey());
                stmt.setInt(4, entry.getValue());
                stmt.executeUpdate();
            }

            for (Map.Entry<Integer, Integer> entry : accessoryCount.entrySet()) {
                stmt.setInt(1, orderId);
                stmt.setString(2, "accessory");
                stmt.setInt(3, entry.getKey());
                stmt.setInt(4, entry.getValue());
                stmt.executeUpdate();
            }
        }
    }
    protected void updateBooksQuantity(ArrayList<Integer> bookIDs, ServletContext context) throws ClassNotFoundException, SQLException {
        String path = context.getRealPath("/WEB-INF/database.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {

            String sql = "UPDATE Books SET Quantity = Quantity - 1 WHERE BookID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Integer bookId : bookIDs) {
                    stmt.setInt(1, bookId);
                    stmt.executeUpdate();
                }
            }
        }
    }
    protected void updateAccessoriesQuantity(ArrayList<Integer> accessoryIDs, ServletContext context) throws ClassNotFoundException, SQLException {
        String path = context.getRealPath("/WEB-INF/database.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {

            String sql = "UPDATE Accessories SET Quantity = Quantity - 1 WHERE AccessoryID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Integer accessoryId : accessoryIDs) {
                    stmt.setInt(1, accessoryId);
                    stmt.executeUpdate();
                }
            }
        }
    }
    protected void updateUserBalance(int userID, double price, ServletContext context, HttpSession session) throws ClassNotFoundException, SQLException {
        String path = context.getRealPath("/WEB-INF/database.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            String sql = "UPDATE Users SET Balance = Balance - ? WHERE UserID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, price);
                stmt.setInt(2, userID);
                stmt.executeUpdate();
            }
            double oldUserBalance = 0;
            String sql2 = "SELECT Balance FROM Users WHERE UserID = ?";
            try (PreparedStatement stmt2 = conn.prepareStatement(sql2)) {
                stmt2.setInt(1, userID);
                try (ResultSet rs = stmt2.executeQuery()) {
                    if (rs.next()) {
                        oldUserBalance = rs.getDouble("Balance");
                    }
                }
            }
            session.setAttribute("balance", oldUserBalance);
        }

    }
}