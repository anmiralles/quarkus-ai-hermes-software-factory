package com.example.coffeeshop.control;

import java.util.List;

import com.example.coffeeshop.entity.Coffee;

/**
 * One page of the catalogue as the control layer returns it: the rows plus the totals the
 * boundary needs for {@code CoffeePageResponse}.
 *
 * <p>Why not {@code Page<Coffee>} as spec section 5 prints it: {@code io.quarkus.panache.common.Page}
 * carries no type parameter, so {@code Page<Coffee>} does not compile, and spec section 1.2
 * forbids {@code control} from importing {@code io.quarkus.panache.common.*} at all. This is a
 * control-owned value type, not a DTO: {@code boundary.dto} never appears in a control
 * signature. The deviation is reported on cards {@code t_8046549a} and {@code t_db2182b6}.
 *
 * @param page 0-based page index that produced {@code content} (the effective value, not the default)
 * @param size page size that produced {@code content} (the effective value, not the default)
 */
public record CoffeePage(List<Coffee> content, int page, int size, long totalElements) {

    public CoffeePage {
        content = List.copyOf(content);
    }
}
