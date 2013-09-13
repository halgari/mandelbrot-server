float calc_iteration(float xpx, float ypx, float max, float width, float height)
{
        // Scale x & y to within (-2.5, -1) to (1, 1)
        float x0 = ((xpx / width) * (float)3.5) - (float)2.5;
        float y0 = ((ypx / height) * (float)2) - (float)1;

        float x = 0;
        float y = 0;

        float iteration = 0;

        while ((x * x) + (y * y) < (2 * 2) && iteration < max)
            {
                float xtemp = (x * x) - (y * y) + x0;
                y = (2 * x * y) + y0;
                x = xtemp;
                iteration += 1;
            }
        return iteration;
}



__kernel void mandelbrot(__global float* out, int width, int height, float max)
{
        int x = get_global_id(0);
        int y = get_global_id(1);

        int out_offset = (y * width) + x;


        out[out_offset] = calc_iteration((float)x, (float)y, max, (float)width, (float)height) / max;

}
