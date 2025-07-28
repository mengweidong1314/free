package com.example.user.controller;

import com.example.user.entity.User;
import com.example.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 用户控制器
 * 提供高性能的RESTful API
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 根据ID获取用户
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    /**
     * 异步获取用户
     */
    @GetMapping("/{id}/async")
    public CompletableFuture<ResponseEntity<User>> getUserByIdAsync(@PathVariable Long id) {
        return userService.getUserByIdAsync(id)
                .thenApply(user -> user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build());
    }

    /**
     * 批量获取用户
     */
    @PostMapping("/batch")
    public ResponseEntity<List<User>> getUsersByIds(@RequestBody List<Long> ids) {
        List<User> users = userService.getUsersByIds(ids);
        return ResponseEntity.ok(users);
    }

    /**
     * 异步批量获取用户
     */
    @PostMapping("/batch/async")
    public CompletableFuture<ResponseEntity<List<User>>> getUsersByIdsAsync(@RequestBody List<Long> ids) {
        return userService.getUsersByIdsAsync(ids)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * 创建用户
     */
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User createdUser = userService.createUser(user);
        return createdUser != null ? ResponseEntity.ok(createdUser) : ResponseEntity.badRequest().build();
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        boolean success = userService.updateUser(user);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        boolean success = userService.deleteUser(id);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    /**
     * 搜索用户
     */
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String keyword) {
        List<User> users = userService.searchUsers(keyword);
        return ResponseEntity.ok(users);
    }

    /**
     * 异步搜索用户
     */
    @GetMapping("/search/async")
    public CompletableFuture<ResponseEntity<List<User>>> searchUsersAsync(@RequestParam String keyword) {
        return userService.searchUsersAsync(keyword)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * 获取用户统计
     */
    @GetMapping("/stats/count")
    public ResponseEntity<Long> getUserCount() {
        Long count = userService.getUserCount();
        return ResponseEntity.ok(count);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("User Service is running");
    }
}