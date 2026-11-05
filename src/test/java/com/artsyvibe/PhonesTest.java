package com.artsyvibe;

import com.artsyvibe.web.Phones;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhonesTest {

    @Test
    void acceptsCommonFormatsAndStoresDigitsOnly() {
        assertThat(Phones.normalize("+973-3311-2201")).isEqualTo("+97333112201");
        assertThat(Phones.normalize(" +973 3311 2201 ")).isEqualTo("+97333112201");
        assertThat(Phones.normalize("(973) 3311.2201")).isEqualTo("97333112201");
        assertThat(Phones.normalize("0097333112201")).isEqualTo("+97333112201");
    }

    @Test
    void rejectsNonsense() {
        assertThatThrownBy(() -> Phones.normalize("call me")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Phones.normalize("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Phones.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
