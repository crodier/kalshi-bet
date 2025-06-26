import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: 10, // number of virtual users
    duration: '30s',
};

const BASE_URL = 'http://localhost:8080';

// TODO because liquidity, this test is not so useful
// we need to be more sophisticated in how we test this
export default function () {
    // Each VU gets a unique userId
    const userId = `demo-user-${__VU}`;

    // Randomly choose buy or sell
    const side = Math.random() < 0.5 ? 'buy' : 'sell';

    // Place an order
    const orderPayload = JSON.stringify({
        symbol: 'KXBTCRESERVE-26-JAN01',
        side: side,
        quantity: 1.0,
        price: 55.0,
    });
    const orderHeaders = { 'Content-Type': 'application/json' };
    let orderRes = http.post(`${BASE_URL}/order`, orderPayload, { headers: orderHeaders });
    check(orderRes, {
        'order placed': (r) => r.status === 200,
    });
    console.log(`Order request duration: ${orderRes.timings.duration} ms`);
    console.log(`Order response body: ${orderRes.body}`);

    // Fetch portfolio
    let portfolioRes = http.get(`${BASE_URL}/v1/portfolio?userId=${userId}&page=0&size=10`);
    check(portfolioRes, {
        'portfolio fetched': (r) => r.status === 200,
    });
    console.log(`Portfolio request duration: ${portfolioRes.timings.duration} ms`);

    sleep(0.5);
} 