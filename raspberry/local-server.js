// local-server.js
const express = require('express');
const fs = require('fs');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

// 기기번호 조회 API
app.get('/api/device-serial', (req, res) => {
    try {
        const cpuinfo = fs.readFileSync('/proc/cpuinfo', 'utf8');
        const serialLine = cpuinfo.split('\n').find(line => line.includes('Serial'));
        const serial = serialLine ? serialLine.split(':')[1].trim() : null;
        
        if (serial) {
            res.json({ deviceId: serial });
        } else {
            res.status(404).json({ error: 'Serial number not found' });
        }
    } catch (error) {
        console.error('Error reading device serial:', error);
        res.status(500).json({ error: 'Failed to read device serial' });
    }
});

const PORT = 3001;
app.listen(PORT, '0.0.0.0', () => { //모든 네트워크 인터페이스에서 접근 가능
    console.log(`Local API server running on port ${PORT}`);
});