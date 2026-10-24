const REPOSITORY = "https://github.com/ronrob90/Ronrob90/tree/main";

export interface Project {
  id: string;
  title: string;
  status: string;
  summary: string;
  tech: string[];
  problem: string;
  work: string;
  boundary: string;
  code: string;
  demo: string;
}

export const projects: Project[] = [
  {
    id: "kalshi",
    title: "Kalshi BRTI Market Monitor",
    status: "Read-only research project",
    summary:
      "A Java application that brings together Bitcoin index readings, contract details, and order-book snapshots, with checks for stale or inconsistent data.",
    tech: ["Java", "Spring Boot", "REST APIs", "WebSockets", "JUnit"],
    problem:
      "Price sources can disagree, and a quote can become outdated while a contract moves toward settlement. I needed to check which data belonged to the active window and whether it was fresh enough to compare.",
    work: "Combined BRTI readings with market snapshots, added active-contract and timestamp checks, and worked through pagination and API refresh problems. Invalid or ambiguous inputs produce explicit error states.",
    boundary:
      "The project monitors and analyzes data. It does not execute orders or establish a validated forecasting edge.",
    code: `${REPOSITORY}/projects/kalshi-brti-monitor`,
    demo: "/demos/kalshi/",
  },
  {
    id: "reading-tracker",
    title: "Family Reading Tracker",
    status: "Rebuild in progress",
    summary:
      "An ongoing React and Spring Boot rebuild of a team capstone, with account registration, a family dashboard, book search, and imports from Open Library.",
    tech: ["Java", "Spring Boot", "React", "JPA", "H2", "Open Library"],
    problem:
      "Families need a shared place to organize books and follow reading activity. The original capstone brought these workflows together; the rebuild explores a different frontend and persistence layer.",
    work: "The available rebuild uses React, JPA repositories, and a local H2 database. Its backend also includes reading-session, milestone, and prize endpoints. The browser demo covers the dashboard and book-import workflow with fictional data.",
    boundary:
      "The original Vue/PostgreSQL capstone was collaborative. This rebuild is a local prototype; family-level access controls and the remaining reading-progress screens are unfinished. Credits and scope are documented with the source.",
    code: `${REPOSITORY}/projects/family-reading-tracker`,
    demo: "/demos/reading-tracker/",
  },
  {
    id: "tenmo",
    title: "TEnmo",
    status: "Bootcamp project",
    summary:
      "A Java payment-service project with a Spring Boot REST API and command-line client, covering accounts, authentication, and transfer workflows.",
    tech: ["Java", "Spring Boot", "PostgreSQL", "JDBC", "JWT"],
    problem:
      "Model account transfers and transaction states across a client, API, and relational database.",
    work: "Worked with authentication, account management, transfer approval and rejection, JDBC data access, and REST endpoints during Tech Elevator project work.",
    boundary:
      "An educational payment-service project. The linked overview describes its scope; it is not a hosted financial service.",
    code: `${REPOSITORY}/projects/tenmo`,
    demo: "",
  },
  {
    id: "futureproof",
    title: "FutureProof Score",
    status: "In progress",
    summary:
      "An exploratory job-resilience scoring API. Current work focuses on the scoring service and response structure, with further model development planned.",
    tech: ["Java", "Spring Boot", "REST API"],
    problem: "Make the assumptions behind a career-resilience score understandable.",
    work: "Built a Spring Boot scoring API as a starting point for an explainable career tool.",
    boundary:
      "Exploratory project; scores should not be treated as validated predictions of employment outcomes.",
    code: "https://github.com/ronrob90/futureproof-score",
    demo: "",
  },
];
