package io.rsocket.examples.service.pechkin.producer;

import io.rsocket.examples.common.model.Letter;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Evgeny Borisov
 */
@SpringBootTest
public class LetterProducerTest {

    @Autowired
    LetterProducer producer;

    @Test
    public void testProducer() {
        Letter letter = producer.getLetter();
        assertThat(letter).isNotNull();
        assertThat(letter.getContent()).isNotNull();
        assertThat(letter.getLocation()).isNotNull();
        assertThat(letter.getSignature()).isNotNull();
    }
}
