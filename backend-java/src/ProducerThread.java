import java.security.SecureRandom;

/**
 * Producer thread for the Secure Random Generator (SRG).
 * One ProducerThread is created per game when it is registered with the SRG.
 *
 * Continuously generates cryptographically secure random integers using
 * java.security.SecureRandom, then deposits them into the game's GameBuffer.
 * Blocks automatically when the buffer is full (via GameBuffer.produce).
 *
 * A small sleep is introduced to simulate the real-world latency of secure
 * random generation and to avoid busy-spinning at 100% CPU.
 */
public class ProducerThread implements Runnable {

    private final GameBuffer buffer;
    private final SecureRandom secureRandom;

    public ProducerThread(GameBuffer buffer) {
        this.buffer       = buffer;
        this.secureRandom = new SecureRandom(); // CSPRNG
    }

    @Override
    public void run() {
        System.out.println("[SRG-Producer] Started for game: " + buffer.getGameId());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Generate a non-negative random integer
                int number = Math.abs(secureRandom.nextInt());

                // Deposit into the buffer (blocks if full)
                buffer.produce(number);

                // Small delay to simulate hardware RNG latency
                Thread.sleep(10);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore flag
                System.out.println("[SRG-Producer] Interrupted for game: " + buffer.getGameId());
                break;
            }
        }
    }
}
