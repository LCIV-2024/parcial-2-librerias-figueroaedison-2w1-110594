package com.example.libreria.controller;

import com.example.libreria.dto.UserRequestDTO;
import com.example.libreria.dto.UserResponseDTO;
import com.example.libreria.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gestionar usuarios de la librería
 * ⭐ VALE 10 PUNTOS
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Crea un nuevo usuario
     * POST /api/users
     *
     * @param requestDTO Datos del usuario a crear (name, email, phoneNumber)
     * @return Usuario creado con código HTTP 201 (CREATED)
     */
    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRequestDTO requestDTO) {
        UserResponseDTO createdUser = userService.createUser(requestDTO);
        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    /**
     * Obtiene un usuario por su ID
     * GET /api/users/{id}
     *
     * @param id ID del usuario a buscar
     * @return Usuario encontrado con código HTTP 200 (OK)
     * @throws ResourceNotFoundException si el usuario no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        UserResponseDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    /**
     * Obtiene todos los usuarios registrados
     * GET /api/users
     *
     * @return Lista de todos los usuarios con código HTTP 200 (OK)
     */
    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        List<UserResponseDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Actualiza un usuario existente
     * PUT /api/users/{id}
     *
     * @param id ID del usuario a actualizar
     * @param requestDTO Nuevos datos del usuario
     * @return Usuario actualizado con código HTTP 200 (OK)
     * @throws ResourceNotFoundException si el usuario no existe
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDTO requestDTO) {
        UserResponseDTO updatedUser = userService.updateUser(id, requestDTO);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * Elimina un usuario
     * DELETE /api/users/{id}
     *
     * @param id ID del usuario a eliminar
     * @return Código HTTP 204 (NO_CONTENT) si se eliminó correctamente
     * @throws ResourceNotFoundException si el usuario no existe
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

