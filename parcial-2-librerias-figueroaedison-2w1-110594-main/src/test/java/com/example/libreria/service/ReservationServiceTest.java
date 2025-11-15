package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.exception.InsufficientStockException;
import com.example.libreria.exception.ResourceNotFoundException;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.Reservation.ReservationStatus;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import com.example.libreria.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para ReservationService
 * ⭐ VALE 20 PUNTOS
 *
 * Utiliza JUnit 5 y Mockito para probar:
 * - Creación de reservas
 * - Devolución de libros
 * - Cálculo de tarifas
 * - Cálculo de multas por demora
 * - Manejo de excepciones
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests de ReservationService")
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private ReservationService reservationService;

    private User testUser;
    private Book testBook;
    private Reservation testReservation;

    /**
     * Configuración inicial antes de cada test
     * Crea objetos de prueba (usuario, libro, reserva)
     */
    @BeforeEach
    void setUp() {
        // Crear usuario de prueba
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");
        testUser.setPhoneNumber("123456789");

        // Crear libro de prueba
        testBook = new Book();
        testBook.setId(1L);
        testBook.setExternalId(258027L);
        testBook.setTitle("The Lord of the Rings");
        testBook.setAuthor("J. R. R. Tolkien");
        testBook.setDescription("Epic fantasy novel");
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setStockQuantity(10);
        testBook.setAvailableQuantity(10);

        // Crear reserva de prueba
        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setRentalDays(7);
        testReservation.setExpectedReturnDate(LocalDate.now().plusDays(7));
        testReservation.setBaseFee(new BigDecimal("111.93")); // 15.99 * 7
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setLateFee(BigDecimal.ZERO);
        testReservation.setStatus(ReservationStatus.ACTIVE);
    }

    /**
     * Test 1: Crear reserva exitosamente
     * Verifica que se cree una reserva correctamente con todos los datos
     */
    @Test
    @DisplayName("Debe crear una reserva exitosamente")
    void testCreateReservation_Success() {
        // Arrange (Preparar)
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(1L);
        requestDTO.setBookExternalId(258027L);
        requestDTO.setStartDate(LocalDate.now());
        requestDTO.setRentalDays(7);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);

        // Act (Actuar)
        ReservationResponseDTO result = reservationService.createReservation(requestDTO);

        // Assert (Verificar)
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Juan Pérez", result.getUserName());
        assertEquals("The Lord of the Rings", result.getBookTitle());
        assertEquals(new BigDecimal("111.93"), result.getBaseFee());
        assertEquals(ReservationStatus.ACTIVE, result.getStatus());

        // Verificar que se redujo el stock disponible
        verify(bookRepository).save(testBook);
        assertEquals(9, testBook.getAvailableQuantity());
    }

    /**
     * Test 2: Crear reserva con usuario inexistente
     * Debe lanzar ResourceNotFoundException
     */
    @Test
    @DisplayName("Debe lanzar excepción cuando el usuario no existe")
    void testCreateReservation_UserNotFound() {
        // Arrange
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(999L);
        requestDTO.setBookExternalId(258027L);
        requestDTO.setStartDate(LocalDate.now());
        requestDTO.setRentalDays(7);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            reservationService.createReservation(requestDTO);
        });

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    /**
     * Test 3: Crear reserva con libro inexistente
     * Debe lanzar ResourceNotFoundException
     */
    @Test
    @DisplayName("Debe lanzar excepción cuando el libro no existe")
    void testCreateReservation_BookNotFound() {
        // Arrange
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(1L);
        requestDTO.setBookExternalId(999999L);
        requestDTO.setStartDate(LocalDate.now());
        requestDTO.setRentalDays(7);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(bookRepository.findByExternalId(999999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            reservationService.createReservation(requestDTO);
        });

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    /**
     * Test 4: Crear reserva sin stock disponible
     * Debe lanzar InsufficientStockException
     */
    @Test
    @DisplayName("Debe lanzar excepción cuando no hay stock disponible")
    void testCreateReservation_InsufficientStock() {
        // Arrange
        testBook.setAvailableQuantity(0); // Sin stock

        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(1L);
        requestDTO.setBookExternalId(258027L);
        requestDTO.setStartDate(LocalDate.now());
        requestDTO.setRentalDays(7);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));

        // Act & Assert
        assertThrows(InsufficientStockException.class, () -> {
            reservationService.createReservation(requestDTO);
        });

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    /**
     * Test 5: Devolver libro a tiempo (sin multa)
     * Debe cambiar el estado a RETURNED sin calcular multa
     */
    @Test
    @DisplayName("Debe devolver libro a tiempo sin multa")
    void testReturnBook_OnTime() {
        // Arrange
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now().plusDays(7)); // A tiempo

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);

        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        // Assert
        assertNotNull(result);
        assertEquals(ReservationStatus.RETURNED, testReservation.getStatus());
        assertEquals(BigDecimal.ZERO, testReservation.getLateFee());
        assertEquals(new BigDecimal("111.93"), testReservation.getTotalFee());

        // Verificar que se incrementó el stock disponible
        verify(bookRepository).save(testBook);
        assertEquals(11, testBook.getAvailableQuantity());
    }

    /**
     * Test 6: Devolver libro con demora (con multa)
     * Debe calcular multa del 15% por día de demora
     */
    @Test
    @DisplayName("Debe devolver libro con demora y calcular multa")
    void testReturnBook_WithLateFee() {
        // Arrange
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now().plusDays(10)); // 3 días tarde

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(testReservation);

        // Act
        reservationService.returnBook(1L, returnRequest);

        // Assert
        assertEquals(ReservationStatus.OVERDUE, testReservation.getStatus());

        // Multa esperada: 15.99 * 0.15 * 3 días = 7.20
        BigDecimal expectedLateFee = new BigDecimal("7.20");
        assertEquals(expectedLateFee, testReservation.getLateFee());

        // Total esperado: 111.93 + 7.20 = 119.13
        BigDecimal expectedTotal = new BigDecimal("119.13");
        assertEquals(expectedTotal, testReservation.getTotalFee());

        verify(bookRepository).save(testBook);
    }

    /**
     * Test 7: Intentar devolver libro ya devuelto
     * Debe lanzar IllegalStateException
     */
    @Test
    @DisplayName("Debe lanzar excepción al devolver libro ya devuelto")
    void testReturnBook_AlreadyReturned() {
        // Arrange
        testReservation.setStatus(ReservationStatus.RETURNED);
        testReservation.setActualReturnDate(LocalDate.now());

        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now());

        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            reservationService.returnBook(1L, returnRequest);
        });

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    /**
     * Test 8: Cálculo correcto de tarifa base
     * Verifica: precio * días = tarifa
     */
    @Test
    @DisplayName("Debe calcular correctamente la tarifa base")
    void testCalculateBaseFee() {
        // Arrange
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(1L);
        requestDTO.setBookExternalId(258027L);
        requestDTO.setStartDate(LocalDate.now());
        requestDTO.setRentalDays(10);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // Act
        ReservationResponseDTO result = reservationService.createReservation(requestDTO);

        // Assert
        // Tarifa esperada: 15.99 * 10 = 159.90
        BigDecimal expectedFee = new BigDecimal("159.90");
        assertEquals(expectedFee, result.getBaseFee());
    }

    /**
     * Test 9: Obtener reserva por ID
     * Debe retornar la reserva correcta
     */
    @Test
    @DisplayName("Debe obtener reserva por ID")
    void testGetReservationById_Success() {
        // Arrange
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));

        // Act
        ReservationResponseDTO result = reservationService.getReservationById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Juan Pérez", result.getUserName());
    }

    /**
     * Test 10: Obtener reserva inexistente
     * Debe lanzar ResourceNotFoundException
     */
    @Test
    @DisplayName("Debe lanzar excepción cuando la reserva no existe")
    void testGetReservationById_NotFound() {
        // Arrange
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            reservationService.getReservationById(999L);
        });
    }
}

