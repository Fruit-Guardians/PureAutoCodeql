#include <stdlib.h>

int main(void) {
    char buffer[8];
    int index = atoi(getenv("USER_INDEX"));
    buffer[index] = 'x';
    return buffer[0];
}
