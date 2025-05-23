package com.spring.codeamigosbackend.registration.controller;

import com.spring.codeamigosbackend.recommendation.controllers.FrameworkController;
import com.spring.codeamigosbackend.recommendation.dtos.GithubScoreRequest;
import com.spring.codeamigosbackend.registration.model.User;
import com.spring.codeamigosbackend.registration.repository.UserRepository;
import com.spring.codeamigosbackend.registration.service.UserService;
import com.spring.codeamigosbackend.registration.exception.UserAlreadyExistsException;
import com.spring.codeamigosbackend.registration.exception.InvalidCredentialsException;
import com.spring.codeamigosbackend.subscription.model.PaymentOrder;
import com.spring.codeamigosbackend.subscription.repository.PaymentOrderRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final FrameworkController frameworkController;
    private final PaymentOrderRepository paymentOrderRepository;

    private final UserRepository userRepository;

    // Register endpoint
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody User user) {
        try {
            System.out.println("Received user: " + user);
            String id = user.getId();
//            System.out.println("GitHub Username: " + githubUsername);
            Optional<User> existingUser = userRepository.findById(id);
            System.out.println("User from DB: " + existingUser);

            User savedUser;

            User u = null;
            if (existingUser.isPresent()) {
                System.out.println("User already exists. Updating...");

                u = existingUser.get();
                u.setUsername(user.getUsername());
                u.setPassword(user.getPassword());
                u.setDisplayName(user.getDisplayName());
                u.setEmail(user.getEmail());
                u.setLeetcodeUsername(user.getLeetcodeUsername());
                u.setCodechefUsername(user.getCodechefUsername());

                savedUser = userRepository.save(u); // update
            } else {
                System.out.println("New user. Saving...");
                savedUser = userRepository.save(user); // register
            }
            GithubScoreRequest githubScoreRequest = new GithubScoreRequest();
            githubScoreRequest.setUsername(user.getUsername());
            githubScoreRequest.setEmail(user.getEmail());
            githubScoreRequest.setAccessToken(u.getGithubAccessToken());
            frameworkController.setGithubScore(githubScoreRequest);
            return ResponseEntity.ok(savedUser);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Server Error: " + e.getMessage());
        }
    }


    // Login endpoint
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User loginRequest) {
        try {
            User authenticatedUser = userService.authenticateUser(
                    loginRequest.getUsername(), loginRequest.getPassword()
            );
            return ResponseEntity.ok(authenticatedUser);
        } catch (InvalidCredentialsException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get user details
    @GetMapping("/{username}")
    public ResponseEntity<?> getUserDetails(@PathVariable java.lang.String username) {
        try {
            User user = userService.getUserByUsername(username);
            //System.out.println(user);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("User not found.");
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{username}")
    public ResponseEntity<?> updateUserByUsername(@RequestBody User user, @PathVariable java.lang.String username) {
        User user1=userService.updateUser(user, username);
        if(user1==null) {
            return ResponseEntity.badRequest().body("User not found.");
        }
        return ResponseEntity.ok(user1);
    }

    @PostMapping("/create_order")
    @ResponseBody
    public String createOrder(@RequestBody Map<String, String> data) throws RazorpayException {

        System.out.println( "Order created successfully");

        int amt = Integer.parseInt(data.get("amount").toString());
        var client = new RazorpayClient("rzp_test_LADJfV2vRiNrkZ","ESUWr3U8EwK99fD1afKYIA6z");
        //RazorpayClient("Key_id","key_secret");

        JSONObject ob = new JSONObject();
        ob.put("amount", amt*100);
        ob.put("currency", "INR");
        ob.put("receipt", "order_RC_123456789");


        // Creating order
        Order order = client.orders.create(ob);
        System.out.println(order);

        // save this order into database...
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setAmount(order.get("amount")+"");
        paymentOrder.setOrderId(order.get("id")); // correct key
        paymentOrder.setStatus("created");
        paymentOrder.setUserId(data.get("userId").toString());

        Optional<User> user = userRepository.findById(data.get("userId").toString());
        if(user.isPresent()) {
            user.get().setStatus("created");
            userRepository.save(user.get());
        }

        paymentOrderRepository.save(paymentOrder);

        return order.toString(); // Return order details

    }

    @PostMapping("/update_order")
    public ResponseEntity<?> updateOrder(@RequestBody Map<String, Object> data) throws RazorpayException {

        PaymentOrder paymentOrder =  paymentOrderRepository.findByOrderId(data.get("order_id").toString());
        paymentOrder.setPaymentId(data.get("payment_id").toString());
        paymentOrder.setStatus(data.get("status").toString());
        Optional<User> user = userRepository.findById(data.get("userId").toString());
        if(user.isPresent()) {
            user.get().setStatus("paid");
            userRepository.save(user.get());
        }
        paymentOrder.setUserId(data.get("userId").toString());
        paymentOrderRepository.save(paymentOrder);

        System.out.println(data);
        return ResponseEntity.ok(Map.of("msg","updated status successfully"));
    }

    @GetMapping("/get_status/{username}")
    public ResponseEntity<?> getUserStatus(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(Map.of("status", user.getStatus()));
    }

}