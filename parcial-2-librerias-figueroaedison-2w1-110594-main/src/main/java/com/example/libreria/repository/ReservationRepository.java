package com.example.libreria.repository;

import com.example.libreria.model.Reservation;
import com.example.libreria.model.Reservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio para gestionar las reservas de libros
 * ⭐ VALE 10 PUNTOS
 *
 * Este repositorio proporciona métodos para:
 * - Buscar reservas por usuario
 * - Buscar reservas por estado (ACTIVE, RETURNED, OVERDUE)
 * - Buscar reservas activas
 * - Buscar reservas vencidas (que pasaron la fecha de devolución)
 * - Buscar reservas por libro
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Busca todas las reservas de un usuario específico
     *
     * @param userId ID del usuario
     * @return Lista de reservas del usuario
     */
    List<Reservation> findByUserId(Long userId);

    /**
     * Busca reservas por estado
     *
     * @param status Estado de la reserva (ACTIVE, RETURNED, OVERDUE)
     * @return Lista de reservas con ese estado
     */
    List<Reservation> findByStatus(ReservationStatus status);

    /**
     * Obtiene todas las reservas activas (no devueltas)
     * Usa JPQL para hacer la consulta
     *
     * @return Lista de reservas con estado ACTIVE
     */
    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE'")
    List<Reservation> findAllActiveReservations();

    /**
     * Busca reservas vencidas (fecha esperada de devolución pasada y aún no devueltas)
     * Una reserva está vencida cuando:
     * - Su estado es ACTIVE (no ha sido devuelta)
     * - La fecha esperada de devolución es anterior a la fecha actual
     *
     * @param currentDate Fecha actual para comparar
     * @return Lista de reservas vencidas
     */
    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expectedReturnDate < :currentDate")
    List<Reservation> findOverdueReservations(@Param("currentDate") LocalDate currentDate);

    /**
     * Busca reservas activas de un libro específico (por su ID externo)
     * Útil para verificar cuántas copias de un libro están actualmente prestadas
     *
     * @param bookExternalId ID externo del libro (de la API externa)
     * @return Lista de reservas activas de ese libro
     */
    @Query("SELECT r FROM Reservation r WHERE r.book.externalId = :bookExternalId AND r.status = 'ACTIVE'")
    List<Reservation> findActiveReservationsByBookExternalId(@Param("bookExternalId") Long bookExternalId);

    /**
     * Cuenta el número de reservas activas de un libro específico
     * Útil para verificar la disponibilidad antes de crear una nueva reserva
     *
     * @param bookExternalId ID externo del libro
     * @return Número de reservas activas (Long)
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.book.externalId = :bookExternalId AND r.status = 'ACTIVE'")
    Long countActiveReservationsByBookExternalId(@Param("bookExternalId") Long bookExternalId);

    /**
     * Busca todas las reservas de un libro específico (activas o no)
     *
     * @param bookId ID interno del libro en la base de datos
     * @return Lista de todas las reservas de ese libro
     */
    List<Reservation> findByBookId(Long bookId);
}

