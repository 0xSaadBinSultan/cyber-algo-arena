/**
 * Contract for domain objects that can be persisted to a CSV row.
 */
public interface Persistable {

    /**
     * Returns the stable unique identifier used by CSV relationships.
     *
     * @return non-null entity identifier
     */
    String getId();

    /**
     * Serializes this entity into one RFC-4180-style CSV record.
     * Implementations must not include a trailing newline.
     *
     * @return CSV representation of this entity
     */
    String toCsvRow();
}
