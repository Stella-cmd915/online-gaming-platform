/**
 * Circular buffer (bounded queue) that holds pre-generated random integers
 * for a single game. Implements the producer-consumer pattern using only
 * synchronized / wait / notify (no java.util.concurrent).
 *
 * Producer thread (ProducerThread): calls produce() to add numbers.
 * Consumer thread (SRGClientHandler): calls consume() to retrieve a number.
 *
 * The buffer blocks the producer when full and the consumer when empty.
 */
public class GameBuffer {

    private final int[]  buffer;    // circular array
    private final int    capacity;
    private int          count;     // current number of items
    private int          head;      // next read position
    private int          tail;      // next write position

    private final String gameId;    // identifier (game name)
    private final String secret;    // shared secret S for SHA-256 verification

    public GameBuffer(String gameId, String secret, int capacity) {
        this.gameId   = gameId;
        this.secret   = secret;
        this.capacity = capacity;
        this.buffer   = new int[capacity];
        this.count    = 0;
        this.head     = 0;
        this.tail     = 0;
    }

    // -----------------------------------------------------------
    // Producer: add a random number (blocks if buffer is full)
    // -----------------------------------------------------------
    public synchronized void produce(int number) throws InterruptedException {
        // Wait until there is space in the buffer
        while (count == capacity) {
            wait();
        }
        buffer[tail] = number;
        tail = (tail + 1) % capacity;
        count++;
        notifyAll(); // wake any blocked consumers
    }

    // -----------------------------------------------------------
    // Consumer: retrieve a random number (blocks if buffer is empty)
    // -----------------------------------------------------------
    public synchronized int consume() throws InterruptedException {
        // Wait until there is at least one number available
        while (count == 0) {
            wait();
        }
        int number = buffer[head];
        head  = (head + 1) % capacity;
        count--;
        notifyAll(); // wake any blocked producers
        return number;
    }

    // -----------------------------------------------------------
    // Getters
    // -----------------------------------------------------------
    public String getGameId() { return gameId; }
    public String getSecret() { return secret; }

    public synchronized int getCount() { return count; }
}
