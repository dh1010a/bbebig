export class EurekaClient {
  config;
  heartbeatInterval;
  baseUrl;

  constructor(config) {
    this.config = config;
    this.baseUrl = `http://${this.config.eureka.host}:${this.config.eureka.port}${this.config.eureka.servicePath}`;
    console.log(`baseUrl: ${this.baseUrl}`);
  }

  async register() {
    try {
      const response = await fetch(
        `${this.baseUrl}/${this.config.instance.app}`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            instance: this.config.instance,
          }),
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(
          `HTTP error! status: ${response.status} - ${errorText}`
        );
      }

      console.log("서비스가 Eureka에 등록되었습니다");
      this.startHeartbeat();
    } catch (error) {
      console.error("Eureka 등록 실패:", error);
      throw error;
    }
  }

  startHeartbeat() {
    this.heartbeatInterval = setInterval(async () => {
      try {
        const response = await fetch(
          `${this.baseUrl}/${this.config.instance.app}/${this.config.instance.hostName}`,
          {
            method: "PUT",
          }
        );

        if (!response.ok) {
          throw new Error(`하트비트 실패! status: ${response.status}`);
        }
      } catch (error) {
        console.error("하트비트 전송 실패:", error);
      }
    }, 30000); // 30초마다 하트비트 전송
  }

  async deregister() {
    try {
      if (this.heartbeatInterval) {
        clearInterval(this.heartbeatInterval);
      }

      const response = await fetch(
        `${this.baseUrl}/${this.config.instance.app}/${this.config.instance.hostName}`,
        {
          method: "DELETE",
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      console.log("서비스가 Eureka에서 제거되었습니다");
    } catch (error) {
      console.error("Eureka 제거 실패:", error);
      throw error;
    }
  }

  async getInstances(appName) {
    try {
      const response = await fetch(`${this.baseUrl}/${appName}`);

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      return data.application?.instance || [];
    } catch (error) {
      console.error(`${appName} 인스턴스 조회 실패:`, error);
      return [];
    }
  }
}
