package com.example.docs;

import java.util.List;

/**
 * Getting started with the example library.
 */
class GettingStarted
{
    /**
     * Create a list and check its size.
     */
    void createList()
    {
        final List<String> list = List.of("a", "b");

        // Lists know their size
        if (list.size() != 2)
        {
            throw new AssertionError();
        }
    }

    private void helper()
    {
        // Not a sample
    }
}
