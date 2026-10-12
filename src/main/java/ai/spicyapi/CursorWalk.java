package ai.spicyapi;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

// A lazy walk over cursor-paginated results: one request per page, made only when iteration reaches
// it, so stopping the loop stops the fetching. Each call to iterator() starts a fresh walk from the
// first cursor, and every page is requested through the same function, which is what keeps the
// filters and the sort identical on every page.
//
// A cursor that comes back a second time would loop forever and spend the rate limit on nothing,
// so it is refused. The items of the page that carried it are still handed out first; the failure
// comes when the walk would otherwise request that cursor again. It is the service misbehaving
// rather than the caller, which is why it is a protocol failure and not an argument error.
final class CursorWalk<T> implements Iterable<T> {

    /** One fetched page, reduced to what the walk needs. */
    record Page<T>(List<T> items, String nextCursor) {
    }

    private final String firstCursor;
    private final Function<String, Page<T>> fetchPage;
    private final String what;

    CursorWalk(String firstCursor, Function<String, Page<T>> fetchPage, String what) {
        this.firstCursor = firstCursor;
        this.fetchPage = fetchPage;
        this.what = what;
    }

    @Override
    public Iterator<T> iterator() {
        return new Walk();
    }

    private final class Walk implements Iterator<T> {

        private final Set<String> seen = new HashSet<>();
        private Iterator<T> current = List.<T>of().iterator();
        private String cursor = firstCursor;
        private boolean started;
        private boolean repeated;

        Walk() {
            if (firstCursor != null) {
                seen.add(firstCursor);
            }
        }

        @Override
        public boolean hasNext() {
            while (!current.hasNext()) {
                if (repeated) {
                    throw new SpicyProtocolException(what + " returned a cursor it had already returned", 200, null);
                }
                if (started && cursor == null) {
                    return false;
                }
                Page<T> page = fetchPage.apply(cursor);
                started = true;
                current = page.items().iterator();
                String next = page.nextCursor();
                if (next == null || next.isEmpty()) {
                    cursor = null;
                } else if (!seen.add(next)) {
                    cursor = null;
                    repeated = true;
                } else {
                    cursor = next;
                }
            }
            return true;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return current.next();
        }
    }
}
