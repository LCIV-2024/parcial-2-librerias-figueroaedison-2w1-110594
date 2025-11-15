package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.exception.InsufficientStockException;
import com.example.libreria.exception.ResourceNotFoundException;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import com.example.libreria.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar reservas de libros
 * ⭐ VALE 30 PUNTOS - EL MÁS IMPORTANTE DEL EXAMEN
 *
 * Funcionalidades principales:
 * - Crear reservas de libros
 * - Devolver libros prestados
 * - Calcular tarifas base (precio × días)
 * - Calcular multas por demora (15% del precio por día de retraso)
 * - Gestionar el stock disponible de libros
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% por día

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    /**
     * Crea una nueva reserva de libro
     *
     * Validaciones:
     * - El usuario debe existir
     * - El libro debe existir
     * - Debe haber stock disponible
     *
     * Acciones:
     * - Crea la reserva con tarifa base calculada
     * - Reduce la cantidad disponible del libro en 1
     *
     * @param requestDTO Datos de la reserva (userId, bookExternalId, startDate, rentalDays)
     * @return DTO de la reserva creada
     * @throws ResourceNotFoundException si el usuario o libro no existen
     * @throws InsufficientStockException si no hay stock disponible
     */
    @Transactional
    public ReservationResponseDTO createReservation(ReservationRequestDTO requestDTO) {
        log.info("Creando reserva para usuario {} y libro {}", requestDTO.getUserId(), requestDTO.getBookExternalId());

        // 1. Validar que el usuario existe
        User user = userRepository.findById(requestDTO.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + requestDTO.getUserId()));

        // 2. Validar que el libro existe
        Book book = bookRepository.findByExternalId(requestDTO.getBookExternalId())
                .orElseThrow(() -> new ResourceNotFoundException("Libro no encontrado con ID externo: " + requestDTO.getBookExternalId()));

        // 3. Validar que hay stock disponible
        if (book.getAvailableQuantity() <= 0) {
            throw new InsufficientStockException("No hay stock disponible para el libro: " + book.getTitle());
        }

        // 4. Crear la nueva reserva
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setBook(book);
        reservation.setStartDate(requestDTO.getStartDate());
        reservation.setRentalDays(requestDTO.getRentalDays());

        // 5. Calcular fecha esperada de devolución
        LocalDate expectedReturnDate = requestDTO.getStartDate().plusDays(requestDTO.getRentalDays());
        reservation.setExpectedReturnDate(expectedReturnDate);

        // 6. Calcular tarifa base (precio del libro × días de alquiler)
        BigDecimal baseFee = calculateTotalFee(book.getPrice(), requestDTO.getRentalDays());
        reservation.setBaseFee(baseFee);
        reservation.setTotalFee(baseFee); // Inicialmente sin multa
        reservation.setLateFee(BigDecimal.ZERO);

        // 7. Establecer estado activo
        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);

        // 8. Reducir la cantidad disponible del libro
        book.setAvailableQuantity(book.getAvailableQuantity() - 1);
        bookRepository.save(book);

        // 9. Guardar la reserva
        Reservation savedReservation = reservationRepository.save(reservation);

        log.info("Reserva creada exitosamente con ID: {}. Stock disponible del libro '{}': {}",
                savedReservation.getId(), book.getTitle(), book.getAvailableQuantity());

        return convertToDTO(savedReservation);
    }

    /**
     * Procesa la devolución de un libro prestado
     *
     * Validaciones:
     * - La reserva debe existir
     * - La reserva debe estar activa (no devuelta previamente)
     *
     * Acciones:
     * - Registra la fecha de devolución
     * - Calcula multa si hay demora (15% del precio del libro por día)
     * - Actualiza el total fee
     * - Cambia el estado de la reserva (RETURNED u OVERDUE)
     * - Incrementa la cantidad disponible del libro en 1
     *
     * @param reservationId ID de la reserva
     * @param returnRequest DTO con la fecha de devolución
     * @return DTO de la reserva actualizada
     * @throws ResourceNotFoundException si la reserva no existe
     * @throws IllegalStateException si la reserva ya fue devuelta
     */
    @Transactional
    public ReservationResponseDTO returnBook(Long reservationId, ReturnBookRequestDTO returnRequest) {
        log.info("Procesando devolución de libro para reserva ID: {}", reservationId);

        // 1. Buscar la reserva
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservationId));

        // 2. Validar que la reserva está activa
        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("La reserva ya fue devuelta anteriormente");
        }

        // 3. Registrar fecha de devolución
        LocalDate returnDate = returnRequest.getReturnDate();
        reservation.setActualReturnDate(returnDate);

        // 4. Calcular días de demora
        long daysLate = ChronoUnit.DAYS.between(reservation.getExpectedReturnDate(), returnDate);

        // 5. Calcular multa si hay demora
        if (daysLate > 0) {
            BigDecimal lateFee = calculateLateFee(reservation.getBook().getPrice(), daysLate);
            reservation.setLateFee(lateFee);

            // Actualizar total fee (tarifa base + multa)
            BigDecimal totalFee = reservation.getBaseFee().add(lateFee);
            reservation.setTotalFee(totalFee);

            // Cambiar estado a OVERDUE (devuelto con demora)
            reservation.setStatus(Reservation.ReservationStatus.OVERDUE);

            log.warn("Libro devuelto con {} días de demora. Multa aplicada: ${}", daysLate, lateFee);
        } else {
            // Devuelto a tiempo o antes
            reservation.setLateFee(BigDecimal.ZERO);
            reservation.setStatus(Reservation.ReservationStatus.RETURNED);

            log.info("Libro devuelto a tiempo. Sin multas.");
        }

        // 6. Aumentar la cantidad disponible del libro
        Book book = reservation.getBook();
        book.setAvailableQuantity(book.getAvailableQuantity() + 1);
        bookRepository.save(book);

        // 7. Guardar cambios en la reserva
        Reservation updatedReservation = reservationRepository.save(reservation);

        log.info("Devolución procesada exitosamente. Total a pagar: ${}", updatedReservation.getTotalFee());

        return convertToDTO(updatedReservation);
    }

    /**
     * Obtiene una reserva por su ID
     */
    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + id));
        return convertToDTO(reservation);
    }

    /**
     * Obtiene todas las reservas
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las reservas de un usuario específico
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las reservas activas (libros actualmente prestados)
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getActiveReservations() {
        return reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las reservas vencidas (que pasaron la fecha de devolución)
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getOverdueReservations() {
        return reservationRepository.findOverdueReservations(LocalDate.now()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Calcula la tarifa total de la reserva
     * Fórmula: precio del libro × días de alquiler
     *
     * Ejemplo:
     * - Libro: $15.99
     * - Días: 7
     * - Total: $15.99 × 7 = $111.93
     *
     * @param bookPrice Precio del libro
     * @param rentalDays Cantidad de días de alquiler
     * @return Tarifa total redondeada a 2 decimales
     */
    private BigDecimal calculateTotalFee(BigDecimal bookPrice, Integer rentalDays) {
        return bookPrice
                .multiply(new BigDecimal(rentalDays))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula la multa por demora en la devolución
     * Fórmula: 15% del precio del libro × días de demora
     *
     * Ejemplo:
     * - Libro: $15.99
     * - Días de demora: 3
     * - Multa: $15.99 × 0.15 × 3 = $7.20
     *
     * @param bookPrice Precio del libro
     * @param daysLate Cantidad de días de demora
     * @return Multa total redondeada a 2 decimales
     */
    private BigDecimal calculateLateFee(BigDecimal bookPrice, long daysLate) {
        return bookPrice
                .multiply(LATE_FEE_PERCENTAGE)
                .multiply(new BigDecimal(daysLate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Convierte una entidad Reservation a su DTO de respuesta
     *
     * @param reservation Entidad Reservation
     * @return DTO con todos los datos de la reserva
     */
    private ReservationResponseDTO convertToDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUserName(reservation.getUser().getName());
        dto.setBookId(reservation.getBook().getId());
        dto.setBookExternalId(reservation.getBook().getExternalId());
        dto.setBookTitle(reservation.getBook().getTitle());
        dto.setRentalDays(reservation.getRentalDays());
        dto.setStartDate(reservation.getStartDate());
        dto.setExpectedReturnDate(reservation.getExpectedReturnDate());
        dto.setActualReturnDate(reservation.getActualReturnDate());
        dto.setBaseFee(reservation.getBaseFee());
        dto.setTotalFee(reservation.getTotalFee());
        dto.setLateFee(reservation.getLateFee());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());
        return dto;
    }
}

