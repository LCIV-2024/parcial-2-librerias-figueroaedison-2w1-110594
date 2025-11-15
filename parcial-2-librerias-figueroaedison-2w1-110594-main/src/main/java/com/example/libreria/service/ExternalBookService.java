package com.example.libreria.service;

import com.example.libreria.dto.ExternalBookDTO;
import com.example.libreria.model.Book;
import com.example.libreria.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalBookService {

    private final RestTemplate restTemplate;
    private final BookRepository bookRepository;

    @Value("${external.api.books.url}")
    private String externalApiUrl;

    @Transactional
    public List<Book> syncBooksFromExternalApi() {
        log.info("Iniciando sincronización de libros desde API externa: {}", externalApiUrl);

        try {
            ResponseEntity<List<ExternalBookDTO>> response = restTemplate.exchange(
                    externalApiUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ExternalBookDTO>>() {}
            );

            List<ExternalBookDTO> externalBooks = response.getBody();

            if (externalBooks == null || externalBooks.isEmpty()) {
                log.warn("No se obtuvieron libros de la API externa");
                return new ArrayList<>();
            }

            log.info("Se obtuvieron {} libros de la API externa", externalBooks.size());

            List<Book> syncedBooks = new ArrayList<>();

            for (ExternalBookDTO externalBook : externalBooks) {
                Book book = syncBook(externalBook);
                syncedBooks.add(book);
            }

            log.info("Sincronización completada. {} libros procesados", syncedBooks.size());
            return syncedBooks;

        } catch (Exception e) {
            log.error("Error al sincronizar libros desde la API externa", e);
            throw new RuntimeException("Error al sincronizar libros: " + e.getMessage(), e);
        }
    }

    private Book syncBook(ExternalBookDTO externalBook) {
        Book book = bookRepository.findByExternalId(externalBook.getId())
                .orElse(new Book());

        book.setExternalId(externalBook.getId());
        book.setTitle(externalBook.getTitle());

        if (externalBook.getAuthorName() != null && !externalBook.getAuthorName().isEmpty()) {
            book.setAuthor(String.join(", ", externalBook.getAuthorName()));
        } else {
            book.setAuthor("Desconocido");
        }

        if (externalBook.getDescription() != null && !externalBook.getDescription().isEmpty()) {
            book.setDescription(externalBook.getDescription());
        } else {
            StringBuilder desc = new StringBuilder();
            desc.append("Libro: ").append(externalBook.getTitle());
            desc.append(" | Autor: ").append(book.getAuthor());

            if (externalBook.getFirstPublishYear() != null) {
                desc.append(" | Año: ").append(externalBook.getFirstPublishYear());
            }
            if (externalBook.getEditionCount() != null) {
                desc.append(" | Ediciones: ").append(externalBook.getEditionCount());
            }

            book.setDescription(desc.toString());
        }

        if (externalBook.getPrice() != null && externalBook.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            book.setPrice(externalBook.getPrice());
        } else {
            book.setPrice(BigDecimal.valueOf(10.00));
        }

        if (book.getId() == null) {
            book.setStockQuantity(10);
            book.setAvailableQuantity(10);
            log.info("Creando nuevo libro: '{}' (ID externo: {})", book.getTitle(), book.getExternalId());
        } else {
            log.info("Actualizando libro existente: '{}' (ID externo: {})", book.getTitle(), book.getExternalId());
        }

        return bookRepository.save(book);
    }

    @Transactional(readOnly = true)
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Book getBookByExternalId(Long externalId) {
        return bookRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con ID externo: " + externalId));
    }

    @Transactional
    public Book updateBookStock(Long externalId, Integer newStock) {
        Book book = getBookByExternalId(externalId);

        int difference = newStock - book.getStockQuantity();
        book.setStockQuantity(newStock);
        book.setAvailableQuantity(book.getAvailableQuantity() + difference);

        log.info("Stock actualizado para '{}': {} unidades (Disponibles: {})",
                book.getTitle(), newStock, book.getAvailableQuantity());

        return bookRepository.save(book);
    }
}

