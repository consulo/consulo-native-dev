#include <pthread.h>
#include <string.h>
#include <unistd.h>

struct point {
    int x;
    int y;
};

int total;
static volatile int spin = 1;

static int square(int value) {
    int result = value * value; /* SQUARE */
    return result;
}

static void *worker(void *argument) {
    while (spin) {
        usleep(1000); /* WORKER */
    }
    return argument;
}

int main(int argc, char **argv) {
    struct point p = {3, 4};
    int values[3] = {1, 2, 3};
    total = 0;
    for (int i = 0; i < 3; i++) {
        total += square(values[i]); /* LOOP */
    }
    p.x = total; /* AFTER_LOOP */
    if (argc > 1 && strcmp(argv[1], "spin") == 0) {
        pthread_t thread;
        pthread_create(&thread, 0, worker, 0);
        while (spin) {
            usleep(1000);
        }
    }
    return total + p.y - 4; /* RETURN */
}
